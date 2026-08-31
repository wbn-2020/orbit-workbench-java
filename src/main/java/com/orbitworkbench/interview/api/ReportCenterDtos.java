package com.orbitworkbench.interview.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 报告中心（C-02）的响应结构，口径见 `14_报告中心设计.md` §5。 */
public final class ReportCenterDtos {

    private ReportCenterDtos() {
    }

    public record ReportListItem(
            Long reportId,
            Long sessionId,
            String sessionTitle,
            String topicMode,
            String form,
            String round,
            String targetRole,
            String targetExperienceBand,
            Long interviewerId,
            String interviewerName,
            String sessionStatus,
            String reportStatus,
            Integer totalScore,
            int dimensionCount,
            Integer dimensionAverage,
            String hiringRecommendation,
            String scoringRuleVersion,
            String failureReason,
            int retryCount,
            Instant generatedAt,
            Instant scheduledAt,
            Instant createdAt
    ) {}

    public record ReportListResponse(
            List<ReportListItem> items,
            long total,
            int page,
            int size
    ) {}

    public record DimensionAverage(
            String name,
            int sampleCount,
            int average
    ) {}

    public record RuleVersionSampleView(
            String ruleVersion,
            long sampleCount
    ) {}

    public record ScorePoint(
            Long reportId,
            Instant generatedAt,
            int totalScore
    ) {}

    public record ReportSummaryResponse(
            int minTrendSamples,
            String ruleVersion,
            long sampleCount,
            boolean renderable,
            List<DimensionAverage> dimensions,
            List<ScorePoint> scoreSeries,
            List<RuleVersionSampleView> versions
    ) {}

    public record AnswerSourceItem(
            String answerSource,
            long count
    ) {}

    public record ReportDetailView(
            ReportListItem report,
            Map<String, Integer> dimensionScores,
            List<String> strengths,
            List<String> weaknesses,
            List<String> followUpFindings,
            List<String> projectMastery,
            List<String> knowledgeGaps,
            List<String> studySuggestions,
            List<AnswerSourceItem> answerSources,
            String aiModelSnapshot
    ) {}
}
