package com.orbitworkbench.project.domain;

import java.time.Instant;

public class ProjectVersionRecord {

    private Long id;
    private Long projectId;
    private int versionNumber;
    private String sourceType;
    private String sourceFileName;
    private String sourceStorageRef;
    private String sourceContentHash;
    private String status;
    private String knowledgeBuildStatus;
    private int knowledgeChunkCount;
    private int knowledgeBuildAttempts;
    private String knowledgeBuildError;
    private Instant knowledgeBuiltAt;
    private int totalFileCount;
    private int parsedFileCount;
    private int excludedFileCount;
    private int failedFileCount;
    private long totalSizeBytes;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public int getVersionNumber() { return versionNumber; }
    public void setVersionNumber(int versionNumber) { this.versionNumber = versionNumber; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSourceFileName() { return sourceFileName; }
    public void setSourceFileName(String sourceFileName) { this.sourceFileName = sourceFileName; }
    public String getSourceStorageRef() { return sourceStorageRef; }
    public void setSourceStorageRef(String sourceStorageRef) { this.sourceStorageRef = sourceStorageRef; }
    public String getSourceContentHash() { return sourceContentHash; }
    public void setSourceContentHash(String sourceContentHash) { this.sourceContentHash = sourceContentHash; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getKnowledgeBuildStatus() { return knowledgeBuildStatus; }
    public void setKnowledgeBuildStatus(String knowledgeBuildStatus) {
        this.knowledgeBuildStatus = knowledgeBuildStatus;
    }
    public int getKnowledgeChunkCount() { return knowledgeChunkCount; }
    public void setKnowledgeChunkCount(int knowledgeChunkCount) {
        this.knowledgeChunkCount = knowledgeChunkCount;
    }
    public int getKnowledgeBuildAttempts() { return knowledgeBuildAttempts; }
    public void setKnowledgeBuildAttempts(int knowledgeBuildAttempts) {
        this.knowledgeBuildAttempts = knowledgeBuildAttempts;
    }
    public String getKnowledgeBuildError() { return knowledgeBuildError; }
    public void setKnowledgeBuildError(String knowledgeBuildError) {
        this.knowledgeBuildError = knowledgeBuildError;
    }
    public Instant getKnowledgeBuiltAt() { return knowledgeBuiltAt; }
    public void setKnowledgeBuiltAt(Instant knowledgeBuiltAt) { this.knowledgeBuiltAt = knowledgeBuiltAt; }
    public int getTotalFileCount() { return totalFileCount; }
    public void setTotalFileCount(int totalFileCount) { this.totalFileCount = totalFileCount; }
    public int getParsedFileCount() { return parsedFileCount; }
    public void setParsedFileCount(int parsedFileCount) { this.parsedFileCount = parsedFileCount; }
    public int getExcludedFileCount() { return excludedFileCount; }
    public void setExcludedFileCount(int excludedFileCount) { this.excludedFileCount = excludedFileCount; }
    public int getFailedFileCount() { return failedFileCount; }
    public void setFailedFileCount(int failedFileCount) { this.failedFileCount = failedFileCount; }
    public long getTotalSizeBytes() { return totalSizeBytes; }
    public void setTotalSizeBytes(long totalSizeBytes) { this.totalSizeBytes = totalSizeBytes; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
