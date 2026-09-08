package com.orbitworkbench.worklog.domain;

import java.time.Instant;

/** 工作记录写侧实体（`06` 工作沉淀域）。 */
public class WorkLogRecord {

    private Long id;
    private Long userId;
    private String title;
    private String content;
    private WorkLogCategory category;
    private boolean distilled;
    private int version;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public WorkLogCategory getCategory() { return category; }
    public void setCategory(WorkLogCategory category) { this.category = category; }
    public boolean isDistilled() { return distilled; }
    public void setDistilled(boolean distilled) { this.distilled = distilled; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
