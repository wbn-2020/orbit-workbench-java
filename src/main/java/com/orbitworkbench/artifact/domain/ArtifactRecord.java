package com.orbitworkbench.artifact.domain;

import java.time.Instant;

public class ArtifactRecord {

    private Long id;
    private Long workspaceId;
    private Long taskId;
    private Long sourceRunId;
    private String artifactType;
    private String title;
    private String status;
    private Long currentVersionId;
    private Instant createdAt;
    private Instant updatedAt;
    private Integer currentVersionNumber;
    private String currentContentRef;
    private String currentContentFormat;
    private String taskTitle;

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

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public Long getSourceRunId() {
        return sourceRunId;
    }

    public void setSourceRunId(Long sourceRunId) {
        this.sourceRunId = sourceRunId;
    }

    public String getArtifactType() {
        return artifactType;
    }

    public void setArtifactType(String artifactType) {
        this.artifactType = artifactType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getCurrentVersionId() {
        return currentVersionId;
    }

    public void setCurrentVersionId(Long currentVersionId) {
        this.currentVersionId = currentVersionId;
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

    public Integer getCurrentVersionNumber() {
        return currentVersionNumber;
    }

    public void setCurrentVersionNumber(Integer currentVersionNumber) {
        this.currentVersionNumber = currentVersionNumber;
    }

    public String getCurrentContentRef() {
        return currentContentRef;
    }

    public void setCurrentContentRef(String currentContentRef) {
        this.currentContentRef = currentContentRef;
    }

    public String getCurrentContentFormat() {
        return currentContentFormat;
    }

    public void setCurrentContentFormat(String currentContentFormat) {
        this.currentContentFormat = currentContentFormat;
    }

    public String getTaskTitle() {
        return taskTitle;
    }

    public void setTaskTitle(String taskTitle) {
        this.taskTitle = taskTitle;
    }
}
