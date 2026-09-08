package com.orbitworkbench.learning.domain;

import java.time.Instant;

/** 学习目标写侧实体（v2 学习更新域）。 */
public class LearningGoalRecord {

    private Long id;
    private Long userId;
    private String title;
    private String reason;
    private LearningGoalStatus status;
    private int progress;
    private String linkedSkill;
    private String idempotencyKey;
    private int version;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
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
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
