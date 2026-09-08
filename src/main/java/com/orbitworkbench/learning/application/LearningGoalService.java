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
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 学习目标（v2 学习更新域）。新增目标默认进入 ACTIVE、进度 0；
 * 状态与进度由用户通过 PUT 推进。暂不提供删除：数据删除规则未定（D-02），
 * 用户先以 DONE 关闭目标。
 */
@Service
public class LearningGoalService {

    private static final int LIST_LIMIT = 500;

    private final LearningGoalMapper mapper;

    public LearningGoalService(LearningGoalMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<LearningGoalResponse> list(Long userId) {
        return mapper.listByUser(userId, LIST_LIMIT, 0).stream()
                .map(LearningGoalResponse::from)
                .toList();
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
        return LearningGoalResponse.from(requireOwned(userId, id));
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
