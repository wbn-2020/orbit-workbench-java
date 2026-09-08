package com.orbitworkbench.worklog.domain;

import java.time.Instant;

/** 知识卡片写侧实体：蒸馏产物，标签以 JSON 数组字符串落库（`06` 工作沉淀域）。 */
public class KnowledgeCardRecord {

    private Long id;
    private Long userId;
    private String title;
    private String summary;
    private Long sourceLogId;
    private String tagsJson;
    private int version;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public Long getSourceLogId() { return sourceLogId; }
    public void setSourceLogId(Long sourceLogId) { this.sourceLogId = sourceLogId; }
    public String getTagsJson() { return tagsJson; }
    public void setTagsJson(String tagsJson) { this.tagsJson = tagsJson; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
