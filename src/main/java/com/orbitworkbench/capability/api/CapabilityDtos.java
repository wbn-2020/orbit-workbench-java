package com.orbitworkbench.capability.api;

import java.time.Instant;
import java.util.List;

/**
 * 技能图谱（C-04）的响应结构，口径见 `16_技能图谱与能力记录设计.md` §8。
 * 全局 {@code default-property-inclusion: non_null} 生效，可空字段是**整个键缺失**，
 * 前端契约因此把这些字段声明为可选（`14` §7、`15` §8 同一条约定）。
 */
public final class CapabilityDtos {

    private CapabilityDtos() {
    }

    /** 一个维度的均分与其样本数；{@code score} 为 null 表示本期没有该维度的观测。 */
    public record DimensionView(
            String name,
            String category,
            Integer score,
            int sampleCount
    ) {}

    /** 趋势序列上的一个点：总分按生成时间升序，带 reportId/sessionId 便于点回那一场。 */
    public record ScorePoint(
            Long reportId,
            Long sessionId,
            Instant generatedAt,
            int totalScore
    ) {}

    public record RuleVersionSampleView(
            String ruleVersion,
            long sampleCount
    ) {}

    /** 自评层（`16` §6.2）：只有两个四档序数字段，不与观测层换算。 */
    public record SelfAssessmentView(
            String javaSkillLevel,
            String aiSkillLevel,
            Instant updatedAt
    ) {}

    /** 来源计数（`16` §6.3）：这三类在本期只提供「有多少来源」，不提供分数。 */
    public record SourcesView(
            long reportSamples,
            long unversionedReports,
            long practiceItems,
            long masteredPracticeItems,
            long confirmedProjectFacts
    ) {}

    public record CapabilityOverviewResponse(
            Integer rangeDays,
            String ruleVersion,
            int maxScore,
            long sampleCount,
            int minTrendSamples,
            boolean renderable,
            String reason,
            List<DimensionView> dimensions,
            List<ScorePoint> series,
            SelfAssessmentView selfAssessment,
            SourcesView sources,
            List<RuleVersionSampleView> versions
    ) {}

    /** 单维度证据：这一维度的每一次观测都追得到具体某份报告（`16` §6.1）。 */
    public record DimensionEvidenceItem(
            Long reportId,
            Long sessionId,
            Instant generatedAt,
            int score
    ) {}

    public record DimensionEvidenceResponse(
            String name,
            String category,
            Integer rangeDays,
            String ruleVersion,
            int sampleCount,
            List<DimensionEvidenceItem> items
    ) {}
}
