package com.orbitworkbench.jobmatch.domain;

import java.time.Instant;

public class JobPostingRecord {

    private Long id;
    private Long userId;
    private Long applicationId;
    private String company;
    private String title;
    private String city;
    private String salaryNote;
    private String source;
    private Long activeVersionId;
    private boolean archived;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }
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
    public Long getActiveVersionId() { return activeVersionId; }
    public void setActiveVersionId(Long activeVersionId) { this.activeVersionId = activeVersionId; }
    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
