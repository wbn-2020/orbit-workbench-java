package com.orbitworkbench.jobprofile.domain;

import java.time.Instant;
import java.time.LocalDate;

public class JobProfileRecord {

    private Long id;
    private Long userId;
    private String targetRole;
    private String targetExperienceBand;
    private String careerStage;
    private String targetLevel;
    private String targetCompany;
    private String javaSkillLevel;
    private String aiSkillLevel;
    private LocalDate targetInterviewDate;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getTargetRole() { return targetRole; }
    public void setTargetRole(String targetRole) { this.targetRole = targetRole; }
    public String getTargetExperienceBand() { return targetExperienceBand; }
    public void setTargetExperienceBand(String targetExperienceBand) {
        this.targetExperienceBand = targetExperienceBand;
    }
    public String getCareerStage() { return careerStage; }
    public void setCareerStage(String careerStage) { this.careerStage = careerStage; }
    public String getTargetLevel() { return targetLevel; }
    public void setTargetLevel(String targetLevel) { this.targetLevel = targetLevel; }
    public String getTargetCompany() { return targetCompany; }
    public void setTargetCompany(String targetCompany) { this.targetCompany = targetCompany; }
    public String getJavaSkillLevel() { return javaSkillLevel; }
    public void setJavaSkillLevel(String javaSkillLevel) { this.javaSkillLevel = javaSkillLevel; }
    public String getAiSkillLevel() { return aiSkillLevel; }
    public void setAiSkillLevel(String aiSkillLevel) { this.aiSkillLevel = aiSkillLevel; }
    public LocalDate getTargetInterviewDate() { return targetInterviewDate; }
    public void setTargetInterviewDate(LocalDate targetInterviewDate) {
        this.targetInterviewDate = targetInterviewDate;
    }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
