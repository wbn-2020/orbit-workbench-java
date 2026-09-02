package com.orbitworkbench.practice.domain;

import java.time.Instant;
import java.time.LocalDate;

public class PracticeItemRecord {

    private Long id;
    private Long userId;
    private PracticeSource sourceType;
    private Long sourceId;
    private String topic;
    private String question;
    private String referenceAnswer;
    private MasteryStatus masteryStatus;
    private LocalDate nextReviewDate;
    private ReviewDateSource reviewDateSource;
    private boolean archived;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public PracticeSource getSourceType() { return sourceType; }
    public void setSourceType(PracticeSource sourceType) { this.sourceType = sourceType; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getReferenceAnswer() { return referenceAnswer; }
    public void setReferenceAnswer(String referenceAnswer) { this.referenceAnswer = referenceAnswer; }
    public MasteryStatus getMasteryStatus() { return masteryStatus; }
    public void setMasteryStatus(MasteryStatus masteryStatus) { this.masteryStatus = masteryStatus; }
    public LocalDate getNextReviewDate() { return nextReviewDate; }
    public void setNextReviewDate(LocalDate nextReviewDate) { this.nextReviewDate = nextReviewDate; }
    public ReviewDateSource getReviewDateSource() { return reviewDateSource; }
    public void setReviewDateSource(ReviewDateSource reviewDateSource) { this.reviewDateSource = reviewDateSource; }
    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
