package com.orbitworkbench.artifactexport.domain;

import java.time.Instant;

public class ArtifactExportRecord {

    private Long id;
    private Long artifactId;
    private Long artifactVersionId;
    private String exportKey;
    private String requestFingerprint;
    private String format;
    private String status;
    private String storageRef;
    private Long sizeBytes;
    private String contentHash;
    private String errorCode;
    private String errorSummary;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant finishedAt;
    private Long workspaceId;
    private Long taskId;
    private Long sourceRunId;
    private Long datasetId;
    private Long sheetId;
    private String artifactType;
    private String artifactTitle;
    private Integer versionNumber;
    private String sourceContentRef;
    private String sourceContentFormat;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(Long artifactId) {
        this.artifactId = artifactId;
    }

    public Long getArtifactVersionId() {
        return artifactVersionId;
    }

    public void setArtifactVersionId(Long artifactVersionId) {
        this.artifactVersionId = artifactVersionId;
    }

    public String getExportKey() {
        return exportKey;
    }

    public void setExportKey(String exportKey) {
        this.exportKey = exportKey;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public void setRequestFingerprint(String requestFingerprint) {
        this.requestFingerprint = requestFingerprint;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStorageRef() {
        return storageRef;
    }

    public void setStorageRef(String storageRef) {
        this.storageRef = storageRef;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorSummary() {
        return errorSummary;
    }

    public void setErrorSummary(String errorSummary) {
        this.errorSummary = errorSummary;
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

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
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

    public Long getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(Long datasetId) {
        this.datasetId = datasetId;
    }

    public Long getSheetId() {
        return sheetId;
    }

    public void setSheetId(Long sheetId) {
        this.sheetId = sheetId;
    }

    public String getArtifactType() {
        return artifactType;
    }

    public void setArtifactType(String artifactType) {
        this.artifactType = artifactType;
    }

    public String getArtifactTitle() {
        return artifactTitle;
    }

    public void setArtifactTitle(String artifactTitle) {
        this.artifactTitle = artifactTitle;
    }

    public Integer getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(Integer versionNumber) {
        this.versionNumber = versionNumber;
    }

    public String getSourceContentRef() {
        return sourceContentRef;
    }

    public void setSourceContentRef(String sourceContentRef) {
        this.sourceContentRef = sourceContentRef;
    }

    public String getSourceContentFormat() {
        return sourceContentFormat;
    }

    public void setSourceContentFormat(String sourceContentFormat) {
        this.sourceContentFormat = sourceContentFormat;
    }
}
