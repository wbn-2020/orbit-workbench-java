package com.orbitworkbench.workbench.domain;

/** 复盘用面试报告行：结论、总分与规则版本（跨版本不可比，需要如实透传）。 */
public class ReportMetricRow extends WindowRow {

    private String status;
    private Integer totalScore;
    private String hiringRecommendation;
    private String scoringRuleVersion;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getTotalScore() { return totalScore; }
    public void setTotalScore(Integer totalScore) { this.totalScore = totalScore; }
    public String getHiringRecommendation() { return hiringRecommendation; }
    public void setHiringRecommendation(String hiringRecommendation) { this.hiringRecommendation = hiringRecommendation; }
    public String getScoringRuleVersion() { return scoringRuleVersion; }
    public void setScoringRuleVersion(String scoringRuleVersion) { this.scoringRuleVersion = scoringRuleVersion; }
}
