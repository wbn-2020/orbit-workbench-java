package com.orbitworkbench.workbench.application;

import com.orbitworkbench.craft.api.CraftDtos.CraftEffectResponse;
import com.orbitworkbench.craft.application.CraftEffectService;
import com.orbitworkbench.craft.domain.CraftNoteRecord;
import com.orbitworkbench.craft.infrastructure.mapper.CraftNoteMapper;
import com.orbitworkbench.interview.domain.ReportCenterRow;
import com.orbitworkbench.interview.infrastructure.mapper.InterviewReportMapper;
import com.orbitworkbench.learning.domain.LearningGoalRow;
import com.orbitworkbench.learning.infrastructure.mapper.LearningGoalMapper;
import com.orbitworkbench.studyplan.domain.StudyTaskRecord;
import com.orbitworkbench.studyplan.domain.StudyTaskSource;
import com.orbitworkbench.studyplan.infrastructure.mapper.StudyTaskMapper;
import com.orbitworkbench.userfact.domain.UserFactRecord;
import com.orbitworkbench.userfact.infrastructure.mapper.UserFactMapper;
import com.orbitworkbench.workbench.api.GrowthThreadDtos.GrowthThread;
import com.orbitworkbench.workbench.api.GrowthThreadDtos.ThreadStep;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 成长脉络（V55，EvoFlow 二轮对照②「成长可视化」）：把已经存在的溯源链接
 * 聚合成能纵览的链——纯读、零迁移、不新增任何关系。
 *
 * <p>三类链全部来自既有真实字段：
 * 事实 → 目标（V52 source_fact_id）；套路 → 练习任务 → 练熟 → 分数对比
 * （V49 study_task CRAFT + V50 practice_count + V54 effects）；
 * 报告 → 复习任务（REPORT source）。链上只画查得到的节点：
 * 报告被删了就不画这一节，绝不凭空补一条不存在的步骤。
 */
@Service
public class GrowthThreadService {

    private static final int MAX_THREADS = 30;
    private static final int GOAL_LIMIT = 200;

    private final UserFactMapper userFactMapper;
    private final LearningGoalMapper learningGoalMapper;
    private final CraftNoteMapper craftMapper;
    private final StudyTaskMapper studyTaskMapper;
    private final InterviewReportMapper reportMapper;
    private final CraftEffectService craftEffectService;

    public GrowthThreadService(UserFactMapper userFactMapper,
                               LearningGoalMapper learningGoalMapper,
                               CraftNoteMapper craftMapper,
                               StudyTaskMapper studyTaskMapper,
                               InterviewReportMapper reportMapper,
                               CraftEffectService craftEffectService) {
        this.userFactMapper = userFactMapper;
        this.learningGoalMapper = learningGoalMapper;
        this.craftMapper = craftMapper;
        this.studyTaskMapper = studyTaskMapper;
        this.reportMapper = reportMapper;
        this.craftEffectService = craftEffectService;
    }

    @Transactional(readOnly = true)
    public List<GrowthThread> threads(Long userId) {
        List<StudyTaskRecord> tasks = studyTaskMapper.listByUser(userId, null);
        List<GrowthThread> out = new ArrayList<>();
        out.addAll(craftThreads(userId, tasks));
        out.addAll(factThreads(userId));
        out.addAll(reportThreads(userId, tasks));
        return out.size() <= MAX_THREADS ? List.copyOf(out) : List.copyOf(out.subList(0, MAX_THREADS));
    }

    /** 套路链：确认的套路 →（若有）练习任务 →（若练熟）效果。只收录有下游的套路。 */
    private List<GrowthThread> craftThreads(Long userId, List<StudyTaskRecord> tasks) {
        Map<Long, List<StudyTaskRecord>> byCraft = new HashMap<>();
        for (StudyTaskRecord task : tasks) {
            if (task.getSourceType() == StudyTaskSource.CRAFT && task.getSourceId() != null) {
                byCraft.computeIfAbsent(task.getSourceId(), k -> new ArrayList<>()).add(task);
            }
        }
        Map<Long, CraftEffectResponse> effects = new HashMap<>();
        for (CraftEffectResponse effect : craftEffectService.effects(userId)) {
            effects.put(effect.craftId(), effect);
        }
        List<GrowthThread> out = new ArrayList<>();
        for (CraftNoteRecord craft : craftMapper.listByUser(userId)) {
            List<StudyTaskRecord> linked = byCraft.get(craft.getId());
            if (linked == null || linked.isEmpty()) {
                continue; // 没接出练习任务的套路还不算「长成链」，不硬画
            }
            List<ThreadStep> steps = new ArrayList<>();
            for (StudyTaskRecord task : linked) {
                steps.add(new ThreadStep("TASK", task.getId(), task.getTitle(),
                        taskStatusText(task), "/study-plan"));
            }
            CraftEffectResponse effect = effects.get(craft.getId());
            out.add(new GrowthThread("CRAFT",
                    List.of(new ThreadStep("CRAFT", craft.getId(), craft.getTitle(),
                            craft.isPinned() ? "已确认 · 置顶" : "已确认", "/crafts")),
                    steps, effect == null ? null : effect.status()));
        }
        return out;
    }

    /** 事实链：CONFIRMED/ARCHIVED 事实 → 由它转化的目标（V52 链接在目标侧）。 */
    private List<GrowthThread> factThreads(Long userId) {
        Map<Long, UserFactRecord> factsById = new HashMap<>();
        for (UserFactRecord fact : userFactMapper.listByUser(userId)) {
            factsById.put(fact.getId(), fact);
        }
        List<GrowthThread> out = new ArrayList<>();
        for (LearningGoalRow goal : learningGoalMapper.listByUser(userId, GOAL_LIMIT, 0)) {
            if (goal.getSourceFactId() == null) {
                continue;
            }
            UserFactRecord fact = factsById.get(goal.getSourceFactId());
            if (fact == null) {
                continue; // 事实查不到（不应发生，FK 保证）——宁缺毋滥
            }
            out.add(new GrowthThread("FACT",
                    List.of(new ThreadStep("FACT", fact.getId(), fact.getTitle(),
                            factStatusText(fact), "/profile/job")),
                    List.of(new ThreadStep("GOAL", goal.getId(), goal.getTitle(),
                            goalStatusText(goal), "/learning-update")),
                    null));
        }
        return out;
    }

    /** 报告链：已就绪报告 → 由它生成的复习任务（既有 REPORT 来源）。 */
    private List<GrowthThread> reportThreads(Long userId, List<StudyTaskRecord> tasks) {
        Map<Long, List<StudyTaskRecord>> byReport = new HashMap<>();
        for (StudyTaskRecord task : tasks) {
            if (task.getSourceType() == StudyTaskSource.REPORT && task.getSourceId() != null) {
                byReport.computeIfAbsent(task.getSourceId(), k -> new ArrayList<>()).add(task);
            }
        }
        List<GrowthThread> out = new ArrayList<>();
        for (Map.Entry<Long, List<StudyTaskRecord>> entry : byReport.entrySet()) {
            ReportCenterRow report = reportMapper.findOwned(userId, entry.getKey());
            if (report == null) {
                continue; // 报告已不在——不画凭空的起点
            }
            List<ThreadStep> steps = new ArrayList<>();
            for (StudyTaskRecord task : entry.getValue()) {
                steps.add(new ThreadStep("TASK", task.getId(), task.getTitle(),
                        taskStatusText(task), "/study-plan"));
            }
            out.add(new GrowthThread("REPORT",
                    List.of(new ThreadStep("REPORT", report.getReportId(),
                            report.getSessionTitle(), "已出分", "/reports")),
                    steps, null));
        }
        out.sort((a, b) -> Integer.compare(b.steps().size(), a.steps().size()));
        return out;
    }

    private static String taskStatusText(StudyTaskRecord task) {
        if (task.getStatus() == null) {
            return "未知状态";
        }
        return switch (task.getStatus()) {
            case PLANNED -> "计划中";
            case IN_PROGRESS -> "进行中";
            case COMPLETED -> "已完成";
            case POSTPONED -> "已延期";
            case SKIPPED -> "已跳过";
        };
    }

    private static String goalStatusText(LearningGoalRow goal) {
        if (goal.getStatus() == null) {
            return "未知状态";
        }
        return switch (goal.getStatus()) {
            case ACTIVE -> "推进中 · " + goal.getProgress() + "%";
            case PAUSED -> "已暂缓";
            case DONE -> "已完成";
        };
    }

    private static String factStatusText(UserFactRecord fact) {
        return switch (fact.getConfirmationStatus()) {
            case CONFIRMED -> "已确认";
            case ANALYZED -> "待确认";
            case ARCHIVED -> "已归档";
        };
    }
}
