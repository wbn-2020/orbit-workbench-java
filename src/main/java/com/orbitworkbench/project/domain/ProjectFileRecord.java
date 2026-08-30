package com.orbitworkbench.project.domain;

import java.time.Instant;

public class ProjectFileRecord {

    private Long id;
    private Long projectVersionId;
    private String relativePath;
    private String relativePathHash;
    private String mediaType;
    private long sizeBytes;
    private String storageRef;
    private String contentHash;
    private String status;
    private String statusReason;
    private Instant createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectVersionId() { return projectVersionId; }
    public void setProjectVersionId(Long projectVersionId) { this.projectVersionId = projectVersionId; }
    public String getRelativePath() { return relativePath; }
    public void setRelativePath(String relativePath) { this.relativePath = relativePath; }
    public String getRelativePathHash() { return relativePathHash; }
    public void setRelativePathHash(String relativePathHash) { this.relativePathHash = relativePathHash; }
    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }
    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
    public String getStorageRef() { return storageRef; }
    public void setStorageRef(String storageRef) { this.storageRef = storageRef; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getStatusReason() { return statusReason; }
    public void setStatusReason(String statusReason) { this.statusReason = statusReason; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
