package com.orbitworkbench.jobmatch.domain;

import java.time.Instant;

/** 岗位列表行：岗位自身字段 + 活动版本元信息 + 关联投递摘要 + 最近一次匹配时间。 */
public class JobPostingListRow {

    private Long postingId;
    private String company;
    private String title;
    private String city;
    private String salaryNote;
    private String source;
    private boolean archived;
    private Instant updatedAt;
    private Long activeVersionId;
    private Integer activeVersionNumber;
    private Integer requirementCount;
    private Long applicationId;
    private String applicationCompany;
    private String applicationRole;
    private String applicationStage;
    private Instant lastMatchedAt;
    private String lastMatchConfirmationStatus;

    public Long getPostingId() { return postingId; }
    public void setPostingId(Long postingId) { this.postingId = postingId; }
    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getSalaryNote() { return salaryNote; }
    public void setSalaryNote(String salaryNote) { this.salaryNote = salaryNote; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Long getActiveVersionId() { return activeVersionId; }
    public void setActiveVersionId(Long activeVersionId) { this.activeVersionId = activeVersionId; }
    public Integer getActiveVersionNumber() { return activeVersionNumber; }
    public void setActiveVersionNumber(Integer activeVersionNumber) { this.activeVersionNumber = activeVersionNumber; }
    public Integer getRequirementCount() { return requirementCount; }
    public void setRequirementCount(Integer requirementCount) { this.requirementCount = requirementCount; }
    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }
    public String getApplicationCompany() { return applicationCompany; }
    public void setApplicationCompany(String applicationCompany) { this.applicationCompany = applicationCompany; }
    public String getApplicationRole() { return applicationRole; }
    public void setApplicationRole(String applicationRole) { this.applicationRole = applicationRole; }
    public String getApplicationStage() { return applicationStage; }
    public void setApplicationStage(String applicationStage) { this.applicationStage = applicationStage; }
    public Instant getLastMatchedAt() { return lastMatchedAt; }
    public void setLastMatchedAt(Instant lastMatchedAt) { this.lastMatchedAt = lastMatchedAt; }
    public String getLastMatchConfirmationStatus() { return lastMatchConfirmationStatus; }
    public void setLastMatchConfirmationStatus(String lastMatchConfirmationStatus) { this.lastMatchConfirmationStatus = lastMatchConfirmationStatus; }
}
