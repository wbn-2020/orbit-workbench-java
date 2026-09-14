package com.orbitworkbench.craft.domain;

import java.time.Instant;

/**
 * 可复用「本事」（craft，V47）：项目讲述结构、话术模板、排查套路、复盘方法这类
 * 「怎么做」的方法论。与知识块（技术点）和画像事实（我是谁）区分开。
 */
public class CraftNoteRecord {

    private Long id;
    private Long userId;
    private String category;
    private String title;
    /** 什么时候用：没有适用场景的套路等于无效套路。 */
    private String whenToUse;
    private String content;
    private String tagsJson;
    private CraftSource source;
    private CraftStatus confirmationStatus;
    private Integer confidence;
    private boolean pinned;
    private int version;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getWhenToUse() { return whenToUse; }
    public void setWhenToUse(String whenToUse) { this.whenToUse = whenToUse; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getTagsJson() { return tagsJson; }
    public void setTagsJson(String tagsJson) { this.tagsJson = tagsJson; }
    public CraftSource getSource() { return source; }
    public void setSource(CraftSource source) { this.source = source; }
    public CraftStatus getConfirmationStatus() { return confirmationStatus; }
    public void setConfirmationStatus(CraftStatus confirmationStatus) {
        this.confirmationStatus = confirmationStatus;
    }
    public Integer getConfidence() { return confidence; }
    public void setConfidence(Integer confidence) { this.confidence = confidence; }
    public boolean isPinned() { return pinned; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
