package com.orbitworkbench.worklog.domain;

import java.time.Instant;

/** 工作记录读侧投影：列表与详情都用它，不含写侧并发字段。 */
public class WorkLogRow {

    private Long id;
    private String title;
    private String content;
    private WorkLogCategory category;
    private boolean distilled;
    private Instant createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public WorkLogCategory getCategory() { return category; }
    public void setCategory(WorkLogCategory category) { this.category = category; }
    public boolean isDistilled() { return distilled; }
    public void setDistilled(boolean distilled) { this.distilled = distilled; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
