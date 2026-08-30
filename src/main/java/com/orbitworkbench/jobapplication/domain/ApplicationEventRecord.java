package com.orbitworkbench.jobapplication.domain;

import java.time.Instant;

public class ApplicationEventRecord {

    private Long id;
    private Long applicationId;
    private Long userId;
    private String eventType;
    private ApplicationStage fromStage;
    private ApplicationStage toStage;
    private String detail;
    private Instant createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public ApplicationStage getFromStage() { return fromStage; }
    public void setFromStage(ApplicationStage fromStage) { this.fromStage = fromStage; }
    public ApplicationStage getToStage() { return toStage; }
    public void setToStage(ApplicationStage toStage) { this.toStage = toStage; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
