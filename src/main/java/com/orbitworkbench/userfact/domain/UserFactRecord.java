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
    /**
     * 用户最后一次确认「这条事实仍然成立」的时间（V43）。
     * 刻意不由注入更新——否则注入越频繁越显新鲜，时效衰减永远不会触发。
     */
    private Instant lastSeenAt;
    /**
     * 被组装进 AI 请求的次数与最后一次时间（V46 用量治理）。
     * 与 last_seen_at 刻意分开：注入是「被使用」，确认是「仍然成立」，两者不该互相覆盖。
     */
    private int injectionCount;
    private Instant lastInjectedAt;
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
    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }
    public int getInjectionCount() { return injectionCount; }
    public void setInjectionCount(int injectionCount) { this.injectionCount = injectionCount; }
    public Instant getLastInjectedAt() { return lastInjectedAt; }
    public void setLastInjectedAt(Instant lastInjectedAt) { this.lastInjectedAt = lastInjectedAt; }
    public String getArchivedReason() { return archivedReason; }
    public void setArchivedReason(String archivedReason) { this.archivedReason = archivedReason; }
    public String getSourceHint() { return sourceHint; }
    public void setSourceHint(String sourceHint) { this.sourceHint = sourceHint; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
