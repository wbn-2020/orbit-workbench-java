package com.orbitworkbench.worklog.domain;

import java.time.Instant;
import java.time.LocalDate;

/** 知识卡片读侧投影：标签以原始 JSON 字符串返回，由服务层解析为列表。 */
public class KnowledgeCardRow {

    private Long id;
    private String title;
    private String summary;
    private Long sourceLogId;
    private String tagsJson;
    private Integer reviewStage;
    private LocalDate nextReviewDate;
    private Instant lastReviewedAt;
    private Instant createdAt;

    public Integer getReviewStage() { return reviewStage; }
    public void setReviewStage(Integer reviewStage) { this.reviewStage = reviewStage; }
    public LocalDate getNextReviewDate() { return nextReviewDate; }
    public void setNextReviewDate(LocalDate nextReviewDate) { this.nextReviewDate = nextReviewDate; }
    public Instant getLastReviewedAt() { return lastReviewedAt; }
    public void setLastReviewedAt(Instant lastReviewedAt) { this.lastReviewedAt = lastReviewedAt; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public Long getSourceLogId() { return sourceLogId; }
    public void setSourceLogId(Long sourceLogId) { this.sourceLogId = sourceLogId; }
    public String getTagsJson() { return tagsJson; }
    public void setTagsJson(String tagsJson) { this.tagsJson = tagsJson; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
