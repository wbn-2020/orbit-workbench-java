package com.orbitworkbench.knowledge.domain;

import java.time.Instant;

public class ProjectFactRecord {

    private Long id;
    private Long userId;
    private Long projectVersionId;
    private String factType;
    private String title;
    private String content;
    private FactSource source;
    private FactStatus confirmationStatus;
    private Integer confidence;
    private Instant confirmedAt;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getProjectVersionId() { return projectVersionId; }
    public void setProjectVersionId(Long projectVersionId) { this.projectVersionId = projectVersionId; }
    public String getFactType() { return factType; }
    public void setFactType(String factType) { this.factType = factType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public FactSource getSource() { return source; }
    public void setSource(FactSource source) { this.source = source; }
    public FactStatus getConfirmationStatus() { return confirmationStatus; }
    public void setConfirmationStatus(FactStatus confirmationStatus) {
        this.confirmationStatus = confirmationStatus;
    }
    public Integer getConfidence() { return confidence; }
    public void setConfidence(Integer confidence) { this.confidence = confidence; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(Instant confirmedAt) { this.confirmedAt = confirmedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
