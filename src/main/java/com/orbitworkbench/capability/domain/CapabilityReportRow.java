package com.orbitworkbench.capability.domain;

import java.io.Serializable;
import java.time.Instant;

/**
 * 能力聚合的读模型（`16` §6.1）：一份已就绪报告 + 它的维度分原文。
 * 归属由 SQL 里的 {@code s.user_id} 保证（{@code interview_report} 自身没有 user_id）。
 */
public class CapabilityReportRow implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long reportId;
    private Long sessionId;
    private String scoringRuleVersion;
    private Integer totalScore;
    private String dimensionScoresJson;
    private Instant generatedAt;

    public Long getReportId() { return reportId; }
    public void setReportId(Long reportId) { this.reportId = reportId; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public String getScoringRuleVersion() { return scoringRuleVersion; }
    public void setScoringRuleVersion(String scoringRuleVersion) { this.scoringRuleVersion = scoringRuleVersion; }
    public Integer getTotalScore() { return totalScore; }
    public void setTotalScore(Integer totalScore) { this.totalScore = totalScore; }
    public String getDimensionScoresJson() { return dimensionScoresJson; }
    public void setDimensionScoresJson(String dimensionScoresJson) { this.dimensionScoresJson = dimensionScoresJson; }
    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }
}
