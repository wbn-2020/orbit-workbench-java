package com.orbitworkbench.knowledge.domain;

import java.time.Instant;

public class KnowledgeChunkRecord {

    private Long id;
    private Long userId;
    private Long projectVersionId;
    private Long projectFileId;
    private String relativePath;
    private Integer chunkNo;
    private String content;
    private Instant createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getProjectVersionId() { return projectVersionId; }
    public void setProjectVersionId(Long projectVersionId) { this.projectVersionId = projectVersionId; }
    public Long getProjectFileId() { return projectFileId; }
    public void setProjectFileId(Long projectFileId) { this.projectFileId = projectFileId; }
    public String getRelativePath() { return relativePath; }
    public void setRelativePath(String relativePath) { this.relativePath = relativePath; }
    public Integer getChunkNo() { return chunkNo; }
    public void setChunkNo(Integer chunkNo) { this.chunkNo = chunkNo; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
