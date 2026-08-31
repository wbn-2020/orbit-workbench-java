package com.orbitworkbench.interview.domain;

import java.io.Serializable;
import java.time.Instant;

/**
 * 报告中心的读模型（14 §5）：一份报告 join 它所属会话的上下文。
 * 列表、趋势和详情共用这一行结构，归属由 SQL 里的 {@code s.user_id} 保证。
 */
public class ReportCenterRow implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long reportId;
    private Long sessionId;
    private String sessionTitle;
    private String topicMode;
    private String form;
    private String round;
    private String targetRole;
    private String targetExperienceBand;
    private Long interviewerId;
    private String interviewerName;
    private String sessionStatus;
    private String aiModelSnapshot;
    private Instant endedAt;
    private Instant scheduledAt;
    private String reportStatus;
    private Integer totalScore;
    private String dimensionScoresJson;
    private String hiringRecommendation;
    private String scoringRuleVersion;
    private String strengthsJson;
    private String weaknessesJson;
    private String followUpFindingsJson;
    private String projectMasteryJson;
    private String knowledgeGapsJson;
    private String studySuggestionsJson;
    private String failureReason;
    private Integer retryCount;
    private Instant generatedAt;
    private Instant createdAt;

    public Long getReportId() { return reportId; }
    public void setReportId(Long reportId) { this.reportId = reportId; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public String getSessionTitle() { return sessionTitle; }
    public void setSessionTitle(String sessionTitle) { this.sessionTitle = sessionTitle; }
    public String getTopicMode() { return topicMode; }
    public void setTopicMode(String topicMode) { this.topicMode = topicMode; }
    public String getForm() { return form; }
    public void setForm(String form) { this.form = form; }
    public String getRound() { return round; }
    public void setRound(String round) { this.round = round; }
    public String getTargetRole() { return targetRole; }
    public void setTargetRole(String targetRole) { this.targetRole = targetRole; }
    public String getTargetExperienceBand() { return targetExperienceBand; }
    public void setTargetExperienceBand(String targetExperienceBand) {
        this.targetExperienceBand = targetExperienceBand;
    }
    public Long getInterviewerId() { return interviewerId; }
    public void setInterviewerId(Long interviewerId) { this.interviewerId = interviewerId; }
    public String getInterviewerName() { return interviewerName; }
    public void setInterviewerName(String interviewerName) { this.interviewerName = interviewerName; }
    public String getSessionStatus() { return sessionStatus; }
    public void setSessionStatus(String sessionStatus) { this.sessionStatus = sessionStatus; }
    public String getAiModelSnapshot() { return aiModelSnapshot; }
    public void setAiModelSnapshot(String aiModelSnapshot) { this.aiModelSnapshot = aiModelSnapshot; }
    public Instant getEndedAt() { return endedAt; }
    public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }
    public Instant getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(Instant scheduledAt) { this.scheduledAt = scheduledAt; }
    public String getReportStatus() { return reportStatus; }
    public void setReportStatus(String reportStatus) { this.reportStatus = reportStatus; }
    public Integer getTotalScore() { return totalScore; }
    public void setTotalScore(Integer totalScore) { this.totalScore = totalScore; }
    public String getDimensionScoresJson() { return dimensionScoresJson; }
    public void setDimensionScoresJson(String dimensionScoresJson) {
        this.dimensionScoresJson = dimensionScoresJson;
    }
    public String getHiringRecommendation() { return hiringRecommendation; }
    public void setHiringRecommendation(String hiringRecommendation) {
        this.hiringRecommendation = hiringRecommendation;
    }
    public String getScoringRuleVersion() { return scoringRuleVersion; }
    public void setScoringRuleVersion(String scoringRuleVersion) {
        this.scoringRuleVersion = scoringRuleVersion;
    }
    public String getStrengthsJson() { return strengthsJson; }
    public void setStrengthsJson(String strengthsJson) { this.strengthsJson = strengthsJson; }
    public String getWeaknessesJson() { return weaknessesJson; }
    public void setWeaknessesJson(String weaknessesJson) { this.weaknessesJson = weaknessesJson; }
    public String getFollowUpFindingsJson() { return followUpFindingsJson; }
    public void setFollowUpFindingsJson(String followUpFindingsJson) {
        this.followUpFindingsJson = followUpFindingsJson;
    }
    public String getProjectMasteryJson() { return projectMasteryJson; }
    public void setProjectMasteryJson(String projectMasteryJson) {
        this.projectMasteryJson = projectMasteryJson;
    }
    public String getKnowledgeGapsJson() { return knowledgeGapsJson; }
    public void setKnowledgeGapsJson(String knowledgeGapsJson) {
        this.knowledgeGapsJson = knowledgeGapsJson;
    }
    public String getStudySuggestionsJson() { return studySuggestionsJson; }
    public void setStudySuggestionsJson(String studySuggestionsJson) {
        this.studySuggestionsJson = studySuggestionsJson;
    }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
