package com.orbitworkbench.interview.domain;

import java.time.Instant;

public class InterviewSessionRecord {

    private Long id;
    private Long userId;
    private String title;
    private String topicMode;
    private String form;
    private String round;
    private Long interviewerId;
    private String interviewerNameSnapshot;
    private String interviewerSnapshotJson;
    private String projectBindingsJson;
    private Long aiConnectionIdSnapshot;
    private String aiModelSnapshot;
    private String webSearchPolicy;
    private String targetRole;
    private String targetExperienceBand;
    private Integer questionLimit;
    private Integer followUpLimit;
    private Integer turnLimit;
    private Integer durationLimitMinutes;
    private Instant scheduledAt;
    private InterviewSessionStatus status;
    private Instant startedAt;
    private Instant endedAt;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getTopicMode() { return topicMode; }
    public void setTopicMode(String topicMode) { this.topicMode = topicMode; }
    public String getForm() { return form; }
    public void setForm(String form) { this.form = form; }
    public String getRound() { return round; }
    public void setRound(String round) { this.round = round; }
    public Long getInterviewerId() { return interviewerId; }
    public void setInterviewerId(Long interviewerId) { this.interviewerId = interviewerId; }
    public String getInterviewerNameSnapshot() { return interviewerNameSnapshot; }
    public void setInterviewerNameSnapshot(String interviewerNameSnapshot) {
        this.interviewerNameSnapshot = interviewerNameSnapshot;
    }
    public String getInterviewerSnapshotJson() { return interviewerSnapshotJson; }
    public void setInterviewerSnapshotJson(String interviewerSnapshotJson) {
        this.interviewerSnapshotJson = interviewerSnapshotJson;
    }
    public String getProjectBindingsJson() { return projectBindingsJson; }
    public void setProjectBindingsJson(String projectBindingsJson) {
        this.projectBindingsJson = projectBindingsJson;
    }
    public Long getAiConnectionIdSnapshot() { return aiConnectionIdSnapshot; }
    public void setAiConnectionIdSnapshot(Long aiConnectionIdSnapshot) {
        this.aiConnectionIdSnapshot = aiConnectionIdSnapshot;
    }
    public String getAiModelSnapshot() { return aiModelSnapshot; }
    public void setAiModelSnapshot(String aiModelSnapshot) { this.aiModelSnapshot = aiModelSnapshot; }
    public String getWebSearchPolicy() { return webSearchPolicy; }
    public void setWebSearchPolicy(String webSearchPolicy) { this.webSearchPolicy = webSearchPolicy; }
    public String getTargetRole() { return targetRole; }
    public void setTargetRole(String targetRole) { this.targetRole = targetRole; }
    public String getTargetExperienceBand() { return targetExperienceBand; }
    public void setTargetExperienceBand(String targetExperienceBand) {
        this.targetExperienceBand = targetExperienceBand;
    }
    public Integer getQuestionLimit() { return questionLimit; }
    public void setQuestionLimit(Integer questionLimit) { this.questionLimit = questionLimit; }
    public Integer getFollowUpLimit() { return followUpLimit; }
    public void setFollowUpLimit(Integer followUpLimit) { this.followUpLimit = followUpLimit; }
    public Integer getTurnLimit() { return turnLimit; }
    public void setTurnLimit(Integer turnLimit) { this.turnLimit = turnLimit; }
    public Integer getDurationLimitMinutes() { return durationLimitMinutes; }
    public void setDurationLimitMinutes(Integer durationLimitMinutes) {
        this.durationLimitMinutes = durationLimitMinutes;
    }
    public Instant getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(Instant scheduledAt) { this.scheduledAt = scheduledAt; }
    public InterviewSessionStatus getStatus() { return status; }
    public void setStatus(InterviewSessionStatus status) { this.status = status; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
