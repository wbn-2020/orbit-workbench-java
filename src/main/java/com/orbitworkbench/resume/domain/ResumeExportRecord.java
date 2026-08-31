package com.orbitworkbench.resume.domain;

import java.time.Instant;

public class ResumeExportRecord implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long resumeVersionId;
    private Long userId;
    private String storageRef;
    private String contentHash;
    private Long sizeBytes;
    private String fontName;
    private ResumeExportStatus status;
    private String failureReason;
    private Instant createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getResumeVersionId() { return resumeVersionId; }
    public void setResumeVersionId(Long resumeVersionId) { this.resumeVersionId = resumeVersionId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getStorageRef() { return storageRef; }
    public void setStorageRef(String storageRef) { this.storageRef = storageRef; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }
    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }
    public String getFontName() { return fontName; }
    public void setFontName(String fontName) { this.fontName = fontName; }
    public ResumeExportStatus getStatus() { return status; }
    public void setStatus(ResumeExportStatus status) { this.status = status; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
