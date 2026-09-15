package com.orbitworkbench.learning.application;

import com.orbitworkbench.learning.infrastructure.mapper.LearningGoalMapper;
import com.orbitworkbench.learning.domain.LearningGoalRecord;
import com.orbitworkbench.learning.domain.LearningGoalRow;
import com.orbitworkbench.learning.domain.LearningGoalStatus;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.learning.api.LearningGoalDtos.CreateLearningGoalRequest;
import com.orbitworkbench.learning.api.LearningGoalDtos.LearningGoalResponse;
import com.orbitworkbench.learning.api.LearningGoalDtos.UpdateLearningGoalRequest;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 学习目标（v2 学习更新域）。新增目标默认进入 ACTIVE、进度 0；
 * 无拆解任务时状态与进度由用户通过 PUT 推进。V58 起目标可拆出执行任务，
 * 一旦有了任务，进度从任务完成数派生（读取时计算，不落派生列——V50 mastered 同一纪律）。
 * 暂不提供删除：数据删除规则未定（D-02），用户先以 DONE 关闭目标。
 */
@Service
public class LearningGoalService {

    private static final int LIST_LIMIT = 500;

    private final LearningGoalMapper mapper;
    private final com.orbitworkbench.userfact.infrastructure.mapper.UserFactMapper userFactMapper;
    private final com.orbitworkbench.studyplan.application.StudyTaskService studyTaskService;

    public LearningGoalService(LearningGoalMapper mapper,
                               com.orbitworkbench.userfact.infrastructure.mapper.UserFactMapper userFactMapper,
                               com.orbitworkbench.studyplan.application.StudyTaskService studyTaskService) {
        this.mapper = mapper;
        this.userFactMapper = userFactMapper;
        this.studyTaskService = studyTaskService;
    }

    @Transactional(readOnly = true)
    public List<LearningGoalResponse> list(Long userId) {
        Map<Long, com.orbitworkbench.studyplan.domain.GoalTaskStats> stats = goalTaskStats(userId);
        return mapper.listByUser(userId, LIST_LIMIT, 0).stream()
                .map(row -> LearningGoalResponse.from(row, stats.get(row.getId())))
                .toList();
    }

    /** V58：目标卡任务统计按用户一次取回（进度派生的单一来源，经 StudyTaskService 读投影不直连 mapper）。 */
    private Map<Long, com.orbitworkbench.studyplan.domain.GoalTaskStats> goalTaskStats(Long userId) {
        Map<Long, com.orbitworkbench.studyplan.domain.GoalTaskStats> byGoal = new HashMap<>();
        for (com.orbitworkbench.studyplan.domain.GoalTaskStats stat : studyTaskService.listGoalTaskStats(userId)) {
            byGoal.put(stat.getGoalId(), stat);
        }
        return byGoal;
    }

    /** V58：给目标拆一步（同名幂等返回 created=0）。目标归属校验在这里，任务侧只管插入。 */
    @Transactional
    public Map<String, Object> addTask(Long userId, Long goalId, String title) {
        requireOwned(userId, goalId);
        int created = studyTaskService.generateFromGoal(userId, goalId, title);
        return Map.of("created", created);
    }

    @Transactional
    public LearningGoalResponse create(Long userId, CreateLearningGoalRequest request, String idempotencyKey) {
        String title = requireText(request.title(), 255, "title");
        String reason = trimToNull(request.reason(), 1024, "reason");
        String linkedSkill = trimToNull(request.linkedSkill(), 128, "linkedSkill");
        String normalizedIdempotencyKey = trimToNull(idempotencyKey, 128, "Idempotency-Key");
        Instant now = Instant.now();
        // 幂等重放必须复用同一请求体：同键但内容不同说明客户端复用了旧密钥，
        // 静默返回旧行会让用户以为新目标已创建，所以显式 409。
        if (normalizedIdempotencyKey != null) {
            LearningGoalRow existing = mapper.findByIdempotencyKey(userId, normalizedIdempotencyKey);
            if (existing != null) {
                if (!Objects.equals(existing.getTitle(), title)
                        || !Objects.equals(existing.getReason(), reason)
                        || !Objects.equals(existing.getLinkedSkill(), linkedSkill)) {
                    throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                            "该幂等键已用于创建另一个学习目标，请修改内容后重新提交");
                }
                return LearningGoalResponse.from(existing);
            }
        }
        LearningGoalRecord record = new LearningGoalRecord();
        record.setUserId(userId);
        record.setTitle(title);
        record.setReason(reason);
        record.setStatus(LearningGoalStatus.ACTIVE);
        record.setProgress(0);
        record.setLinkedSkill(linkedSkill);
        record.setIdempotencyKey(normalizedIdempotencyKey);
        record.setVersion(1);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        mapper.insert(record);
        LearningGoalRow row = requireOwned(userId, record.getId());
        return LearningGoalResponse.from(row);
    }

    /**
     * 推进状态与进度。状态机允许 ACTIVE/PAUSED/DONE 之间自由往返（用户可能重开一个
     * 目标或暂缓它）；DONE 只能由用户显式设置，系统不自动判定完成。
     */
    @Transactional
    public LearningGoalResponse update(Long userId, Long id, UpdateLearningGoalRequest request) {
        requireOwned(userId, id);
        LearningGoalStatus status = parseStatus(request.status());
        int progress = request.progress();
        if (progress < 0 || progress > 100) {
            throw invalid("progress 必须在 0 到 100 之间");
        }
        mapper.updateStatusAndProgress(id, userId, status.name(), progress, Instant.now());
        return responseWithStats(userId, requireOwned(userId, id));
    }

    /**
     * V58：单目标响应也带上拆解统计——否则 PUT/幂等重放返回体会报「taskCount=0」
     * 而列表里明明有任务，同一目标两个端点自相矛盾。
     */
    private LearningGoalResponse responseWithStats(Long userId, LearningGoalRow row) {
        return LearningGoalResponse.from(row, goalTaskStats(userId).get(row.getId()));
    }

    /**
     * V52：把一条已确认画像事实转成学习目标（事实 → 目标的横向链接）。
     * 幂等按 source_fact_id 判定——一条事实只转化一次，重复点击返回 409 由前端回显「已成目标」。
     * 候选池（ANALYZED）事实不能转：未确认的推断不是用户的自我认知。
     */
    @Transactional
    public LearningGoalResponse createFromFact(Long userId, Long factId) {
        com.orbitworkbench.userfact.domain.UserFactRecord fact =
                userFactMapper.findByIdAndUser(factId, userId);
        if (fact == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "用户事实不存在");
        }
        if (fact.getConfirmationStatus()
                != com.orbitworkbench.userfact.domain.UserFactStatus.CONFIRMED) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "只有已确认的画像事实可以转成学习目标");
        }
        if (mapper.countBySourceFact(userId, factId) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "这条事实已经转成学习目标了");
        }
        Instant now = Instant.now();
        LearningGoalRecord record = new LearningGoalRecord();
        record.setUserId(userId);
        record.setTitle(fact.getTitle());
        record.setReason(fact.getContent());
        record.setStatus(LearningGoalStatus.ACTIVE);
        record.setProgress(0);
        record.setLinkedSkill(null);
        record.setSourceFactId(factId);
        record.setIdempotencyKey(null);
        record.setVersion(1);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        mapper.insert(record);
        return LearningGoalResponse.from(requireOwned(userId, record.getId()));
    }

    private LearningGoalRow requireOwned(Long userId, Long id) {
        LearningGoalRow row = mapper.findOwned(userId, id);
        if (row == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "学习目标不存在");
        }
        return row;
    }

    private static LearningGoalStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            throw invalid("status 不能为空");
        }
        try {
            return LearningGoalStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw invalid("status 取值不合法，可选：ACTIVE、PAUSED、DONE");
        }
    }

    private static String requireText(String raw, int maxChars, String field) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            throw invalid(field + " 不能为空");
        }
        if (value.length() > maxChars) {
            throw invalid(field + " 长度不得超过 " + maxChars + " 字");
        }
        return value;
    }

    private static String trimToNull(String raw, int maxChars, String field) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.length() > maxChars) {
            throw invalid(field + " 长度不得超过 " + maxChars + " 字");
        }
        return value;
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST, message);
    }
}
