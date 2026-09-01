package com.orbitworkbench.practice.domain;

import java.time.Instant;

public class PracticeAttemptRecord {

    private Long id;
    private Long practiceItemId;
    private String answer;
    private Integer selfScore;
    private PracticeResult result;
    private String feedback;
    private Instant attemptedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPracticeItemId() { return practiceItemId; }
    public void setPracticeItemId(Long practiceItemId) { this.practiceItemId = practiceItemId; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public Integer getSelfScore() { return selfScore; }
    public void setSelfScore(Integer selfScore) { this.selfScore = selfScore; }
    public PracticeResult getResult() { return result; }
    public void setResult(PracticeResult result) { this.result = result; }
    public String getFeedback() { return feedback; }
    public void setFeedback(String feedback) { this.feedback = feedback; }
    public Instant getAttemptedAt() { return attemptedAt; }
    public void setAttemptedAt(Instant attemptedAt) { this.attemptedAt = attemptedAt; }
}
