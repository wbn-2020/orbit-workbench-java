package com.orbitworkbench.jobmatch.domain;

import java.time.Instant;

/** 匹配历史行：一次保存的结果 + 它当时用的 JD 版本与简历版本，用于回看「当时比的是什么」。 */
public class JobMatchRow {

    private Long matchId;
    private Long jobPostingVersionId;
    private Integer jdVersionNumber;
    private Long resumeVersionId;
    private Integer resumeVersionNumber;
    private String resumeStatus;
    private String ruleVersion;
    private Integer totalScore;
    private Integer skillScore;
    private Integer experienceScore;
    private Integer projectScore;
    private String matchedJson;
    private String gapsJson;
    private String profileSnapshotJson;
    private MatchConfirmation confirmationStatus;
    private Instant confirmedAt;
    private Instant createdAt;

    public Long getMatchId() { return matchId; }
    public void setMatchId(Long matchId) { this.matchId = matchId; }
    public Long getJobPostingVersionId() { return jobPostingVersionId; }
    public void setJobPostingVersionId(Long jobPostingVersionId) { this.jobPostingVersionId = jobPostingVersionId; }
    public Integer getJdVersionNumber() { return jdVersionNumber; }
    public void setJdVersionNumber(Integer jdVersionNumber) { this.jdVersionNumber = jdVersionNumber; }
    public Long getResumeVersionId() { return resumeVersionId; }
    public void setResumeVersionId(Long resumeVersionId) { this.resumeVersionId = resumeVersionId; }
    public Integer getResumeVersionNumber() { return resumeVersionNumber; }
    public void setResumeVersionNumber(Integer resumeVersionNumber) { this.resumeVersionNumber = resumeVersionNumber; }
    public String getResumeStatus() { return resumeStatus; }
    public void setResumeStatus(String resumeStatus) { this.resumeStatus = resumeStatus; }
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
    public String getProfileSnapshotJson() { return profileSnapshotJson; }
    public void setProfileSnapshotJson(String profileSnapshotJson) { this.profileSnapshotJson = profileSnapshotJson; }
    public MatchConfirmation getConfirmationStatus() { return confirmationStatus; }
    public void setConfirmationStatus(MatchConfirmation confirmationStatus) { this.confirmationStatus = confirmationStatus; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(Instant confirmedAt) { this.confirmedAt = confirmedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
