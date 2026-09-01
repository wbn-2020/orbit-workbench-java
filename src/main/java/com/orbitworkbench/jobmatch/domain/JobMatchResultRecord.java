package com.orbitworkbench.jobmatch.domain;

import java.time.Instant;

/**
 * 一次匹配结果。四个分数列按 ADR-0011 允许 {@code null}，语义是「未评分」而不是 0：
 * 本期没有任何可量化的经验/项目输入，写数字就是编。
 */
public class JobMatchResultRecord {

    private Long id;
    private Long userId;
    private Long jobPostingVersionId;
    private Long resumeVersionId;
    private String profileSnapshotJson;
    private String ruleVersion;
    private Integer totalScore;
    private Integer skillScore;
    private Integer experienceScore;
    private Integer projectScore;
    private String matchedJson;
    private String gapsJson;
    private MatchConfirmation confirmationStatus;
    private Instant confirmedAt;
    private Instant createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getJobPostingVersionId() { return jobPostingVersionId; }
    public void setJobPostingVersionId(Long jobPostingVersionId) { this.jobPostingVersionId = jobPostingVersionId; }
    public Long getResumeVersionId() { return resumeVersionId; }
    public void setResumeVersionId(Long resumeVersionId) { this.resumeVersionId = resumeVersionId; }
    public String getProfileSnapshotJson() { return profileSnapshotJson; }
    public void setProfileSnapshotJson(String profileSnapshotJson) { this.profileSnapshotJson = profileSnapshotJson; }
    public String getRuleVersion() { return ruleVersion; }
    public void setRuleVersion(String ruleVersion) { this.ruleVersion = ruleVersion; }
    public Integer getTotalScore() { return totalScore; }
    public void setTotalScore(Integer totalScore) { this.totalScore = totalScore; }
    public Integer getSkillScore() { return skillScore; }
    public void setSkillScore(Integer skillScore) { this.skillScore = skillScore; }
    public Integer getExperienceScore() { return experienceScore; }
    public void setExperienceScore(Integer experienceScore) { this.experienceScore = experienceScore; }
    public Integer getProjectScore() { return projectScore; }
    public void setProjectScore(Integer projectScore) { this.projectScore = projectScore; }
    public String getMatchedJson() { return matchedJson; }
    public void setMatchedJson(String matchedJson) { this.matchedJson = matchedJson; }
    public String getGapsJson() { return gapsJson; }
    public void setGapsJson(String gapsJson) { this.gapsJson = gapsJson; }
    public MatchConfirmation getConfirmationStatus() { return confirmationStatus; }
    public void setConfirmationStatus(MatchConfirmation confirmationStatus) { this.confirmationStatus = confirmationStatus; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(Instant confirmedAt) { this.confirmedAt = confirmedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
