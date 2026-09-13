package com.orbitworkbench.workbench.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 周期复盘响应（v2 三模式收敛点）：把市场感知（面试）、工作沉淀、学习更新
 * 在同一个时间窗口里对齐，回答「这段时间我到底推进了什么」。
 *
 * <p>口径约定（延续项目诚实原则）：
 * 分数平均只统计与当前评分规则版本一致的样本，跨版本样本单独计数、不混入；
 * 没有报告的已完成面试如实计入无分场次，而不是从总数里消失。
 */
public final class ReflectionDtos {

    private ReflectionDtos() {
    }

    public record DailyPoint(
            LocalDate date,
            int focusMinutes,
            int workLogs,
            int knowledgeCards
    ) {}

    public record WorkStats(
            int total,
            Map<String, Integer> byCategory,
            int distilled,
            int pendingDistill
    ) {}

    public record StudyStats(
            int completed,
            int created,
            int outstanding
    ) {}

    public record LearningStats(
            int newGoals,
            int doneGoals
    ) {}

    public record InterviewStats(
            int sessions,
            int scored,
            Integer avgScore,
            int crossVersionSamples,
            Map<String, Integer> byRecommendation
    ) {}

    public record PeriodTotals(
            int focusMinutes,
            int workLogs,
            int knowledgeCards,
            int studyCompleted
    ) {}

    public record ReflectionResponse(
            String period,
            int offset,
            LocalDate rangeStart,
            LocalDate rangeEnd,
            String label,
            WorkStats work,
            LearningStats learning,
            StudyStats study,
            InterviewStats interview,
            List<DailyPoint> daily,
            PeriodTotals previous,
            String scoringRuleVersion
    ) {}
}
