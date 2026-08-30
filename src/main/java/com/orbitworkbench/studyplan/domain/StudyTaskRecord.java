package com.orbitworkbench.studyplan.domain;

import java.time.Instant;
import java.time.LocalDate;

public class StudyTaskRecord {

    private Long id;
    private Long userId;
    private StudyTaskSource sourceType;
    private Long sourceId;
    private String title;
    private String topic;
    private String taskType;
    private StudyTaskPriority priority;
    private Integer estimatedMinutes;
    private LocalDate dueDate;
    private StudyTaskStatus status;
    private Boolean manual;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public StudyTaskSource getSourceType() { return sourceType; }
    public void setSourceType(StudyTaskSource sourceType) { this.sourceType = sourceType; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }
    public StudyTaskPriority getPriority() { return priority; }
    public void setPriority(StudyTaskPriority priority) { this.priority = priority; }
    public Integer getEstimatedMinutes() { return estimatedMinutes; }
    public void setEstimatedMinutes(Integer estimatedMinutes) { this.estimatedMinutes = estimatedMinutes; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public StudyTaskStatus getStatus() { return status; }
    public void setStatus(StudyTaskStatus status) { this.status = status; }
    public Boolean getManual() { return manual; }
    public void setManual(Boolean manual) { this.manual = manual; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
