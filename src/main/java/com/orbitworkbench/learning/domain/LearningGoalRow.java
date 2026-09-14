package com.orbitworkbench.learning.domain;

import java.time.Instant;

/** 学习目标读侧投影。 */
public class LearningGoalRow {

    private Long id;
    private String title;
    private String reason;
    private LearningGoalStatus status;
    private int progress;
    private String linkedSkill;
    /** V52：来源画像事实 id（fact → 目标链接）；普通目标为 null。 */
    private Long sourceFactId;
    private Instant createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public LearningGoalStatus getStatus() { return status; }
    public void setStatus(LearningGoalStatus status) { this.status = status; }
    public int getProgress() { return progress; }
    public void setProgress(int progress) { this.progress = progress; }
    public String getLinkedSkill() { return linkedSkill; }
    public void setLinkedSkill(String linkedSkill) { this.linkedSkill = linkedSkill; }
    public Long getSourceFactId() { return sourceFactId; }
    public void setSourceFactId(Long sourceFactId) { this.sourceFactId = sourceFactId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
