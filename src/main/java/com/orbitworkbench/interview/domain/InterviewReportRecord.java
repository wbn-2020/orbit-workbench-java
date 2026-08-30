package com.orbitworkbench.interview.domain;

import java.time.Instant;

public class InterviewReportRecord {

    private Long id;
    private Long sessionId;
    private ReportStatus status;
    private Integer totalScore;
    private String dimensionScoresJson;
    private String hiringRecommendation;
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
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public ReportStatus getStatus() { return status; }
    public void setStatus(ReportStatus status) { this.status = status; }
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
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
