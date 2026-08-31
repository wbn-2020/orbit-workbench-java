package com.orbitworkbench.resume.domain;

import java.time.Instant;

public class ResumeVersionRecord implements java.io.Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long resumeId;
    private Integer versionNumber;
    private ResumeVersionStatus status;
    private String sectionsJson;
    private String sourceSnapshotJson;
    private String changeSummary;
    private String pdfStorageRef;
    private Instant pdfGeneratedAt;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getResumeId() { return resumeId; }
    public void setResumeId(Long resumeId) { this.resumeId = resumeId; }
    public Integer getVersionNumber() { return versionNumber; }
    public void setVersionNumber(Integer versionNumber) { this.versionNumber = versionNumber; }
    public ResumeVersionStatus getStatus() { return status; }
    public void setStatus(ResumeVersionStatus status) { this.status = status; }
    public String getSectionsJson() { return sectionsJson; }
    public void setSectionsJson(String sectionsJson) { this.sectionsJson = sectionsJson; }
    public String getSourceSnapshotJson() { return sourceSnapshotJson; }
    public void setSourceSnapshotJson(String sourceSnapshotJson) { this.sourceSnapshotJson = sourceSnapshotJson; }
    public String getChangeSummary() { return changeSummary; }
    public void setChangeSummary(String changeSummary) { this.changeSummary = changeSummary; }
    public String getPdfStorageRef() { return pdfStorageRef; }
    public void setPdfStorageRef(String pdfStorageRef) { this.pdfStorageRef = pdfStorageRef; }
    public Instant getPdfGeneratedAt() { return pdfGeneratedAt; }
    public void setPdfGeneratedAt(Instant pdfGeneratedAt) { this.pdfGeneratedAt = pdfGeneratedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
