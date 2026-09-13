package com.orbitworkbench.userfact.domain;

import java.time.Instant;

/** 用户级画像事实（个人记忆层），字段语义对齐 project_fact，但跨项目长期有效。 */
public class UserFactRecord {

    private Long id;
    private Long userId;
    private String factType;
    private String title;
    private String content;
    private UserFactSource source;
    private UserFactStatus confirmationStatus;
    private Integer confidence;
    private Instant confirmedAt;
    private String archivedReason;
    private String sourceHint;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getFactType() { return factType; }
    public void setFactType(String factType) { this.factType = factType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public UserFactSource getSource() { return source; }
    public void setSource(UserFactSource source) { this.source = source; }
    public UserFactStatus getConfirmationStatus() { return confirmationStatus; }
    public void setConfirmationStatus(UserFactStatus confirmationStatus) {
        this.confirmationStatus = confirmationStatus;
    }
    public Integer getConfidence() { return confidence; }
    public void setConfidence(Integer confidence) { this.confidence = confidence; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(Instant confirmedAt) { this.confirmedAt = confirmedAt; }
    public String getArchivedReason() { return archivedReason; }
    public void setArchivedReason(String archivedReason) { this.archivedReason = archivedReason; }
    public String getSourceHint() { return sourceHint; }
    public void setSourceHint(String sourceHint) { this.sourceHint = sourceHint; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
