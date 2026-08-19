package com.orbitworkbench.task.domain;

import java.time.Instant;

public class TaskRecord {

    private Long id;
    private Long workspaceId;
    private Long connectionId;
    private String moduleType;
    private String title;
    private String description;
    private String expectedArtifactType;
    private String priority;
    private String status;
    private Long currentRunId;
    private String currentRunStatus;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(Long workspaceId) {
        this.workspaceId = workspaceId;
    }

    public Long getConnectionId() {
        return connectionId;
    }

    public void setConnectionId(Long connectionId) {
        this.connectionId = connectionId;
    }

    public String getModuleType() {
        return moduleType;
    }

    public void setModuleType(String moduleType) {
        this.moduleType = moduleType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getExpectedArtifactType() {
        return expectedArtifactType;
    }

    public void setExpectedArtifactType(String expectedArtifactType) {
        this.expectedArtifactType = expectedArtifactType;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getCurrentRunId() {
        return currentRunId;
    }

    public void setCurrentRunId(Long currentRunId) {
        this.currentRunId = currentRunId;
    }

    public String getCurrentRunStatus() {
        return currentRunStatus;
    }

    public void setCurrentRunStatus(String currentRunStatus) {
        this.currentRunStatus = currentRunStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
