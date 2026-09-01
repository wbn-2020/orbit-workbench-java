package com.orbitworkbench.practice.domain;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 错题本读侧投影（`15` §7）：条目 + 一次查询里带出的来源追溯信息。
 *
 * <p>报告来源只能追到报告与会话，轮次来源还能追到原始问答，两者能填的字段不同，
 * 因此这里保留 null 让服务层如实表达「追不到」而不是补一个猜测值。
 */
public class PracticeItemRow {

    private Long itemId;
    private PracticeSource sourceType;
    private Long sourceId;
    private String topic;
    private String question;
    private String referenceAnswer;
    private MasteryStatus masteryStatus;
    private LocalDate nextReviewDate;
    private boolean archived;
    private Instant createdAt;
    private Instant updatedAt;
    private Long attemptCount;
    private Instant lastAttemptAt;
    private PracticeResult lastResult;
    private Integer lastSelfScore;
    private Long sourceSessionId;
    private String sourceSessionTitle;
    private String sourceTopicMode;
    private String sourceForm;
    private Integer reportTotalScore;
    private String reportScoringRuleVersion;
    private String originalQuestion;
    private String originalAnswer;
    private String originalAnswerSource;
    private String originalTurnType;

    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }
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
    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Long getAttemptCount() { return attemptCount; }
    public void setAttemptCount(Long attemptCount) { this.attemptCount = attemptCount; }
    public Instant getLastAttemptAt() { return lastAttemptAt; }
    public void setLastAttemptAt(Instant lastAttemptAt) { this.lastAttemptAt = lastAttemptAt; }
    public PracticeResult getLastResult() { return lastResult; }
    public void setLastResult(PracticeResult lastResult) { this.lastResult = lastResult; }
    public Integer getLastSelfScore() { return lastSelfScore; }
    public void setLastSelfScore(Integer lastSelfScore) { this.lastSelfScore = lastSelfScore; }
    public Long getSourceSessionId() { return sourceSessionId; }
    public void setSourceSessionId(Long sourceSessionId) { this.sourceSessionId = sourceSessionId; }
    public String getSourceSessionTitle() { return sourceSessionTitle; }
    public void setSourceSessionTitle(String sourceSessionTitle) { this.sourceSessionTitle = sourceSessionTitle; }
    public String getSourceTopicMode() { return sourceTopicMode; }
    public void setSourceTopicMode(String sourceTopicMode) { this.sourceTopicMode = sourceTopicMode; }
    public String getSourceForm() { return sourceForm; }
    public void setSourceForm(String sourceForm) { this.sourceForm = sourceForm; }
    public Integer getReportTotalScore() { return reportTotalScore; }
    public void setReportTotalScore(Integer reportTotalScore) { this.reportTotalScore = reportTotalScore; }
    public String getReportScoringRuleVersion() { return reportScoringRuleVersion; }
    public void setReportScoringRuleVersion(String reportScoringRuleVersion) {
        this.reportScoringRuleVersion = reportScoringRuleVersion;
    }
    public String getOriginalQuestion() { return originalQuestion; }
    public void setOriginalQuestion(String originalQuestion) { this.originalQuestion = originalQuestion; }
    public String getOriginalAnswer() { return originalAnswer; }
    public void setOriginalAnswer(String originalAnswer) { this.originalAnswer = originalAnswer; }
    public String getOriginalAnswerSource() { return originalAnswerSource; }
    public void setOriginalAnswerSource(String originalAnswerSource) { this.originalAnswerSource = originalAnswerSource; }
    public String getOriginalTurnType() { return originalTurnType; }
    public void setOriginalTurnType(String originalTurnType) { this.originalTurnType = originalTurnType; }
}
