package com.orbitworkbench.interview.domain;

import java.time.Instant;

public class InterviewTurnRecord {

    private Long id;
    private Long sessionId;
    private Integer turnNo;
    private InterviewTurnType turnType;
    private String question;
    private String answer;
    private AnswerSource answerSource;
    private Instant createdAt;
    private Instant answeredAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public Integer getTurnNo() { return turnNo; }
    public void setTurnNo(Integer turnNo) { this.turnNo = turnNo; }
    public InterviewTurnType getTurnType() { return turnType; }
    public void setTurnType(InterviewTurnType turnType) { this.turnType = turnType; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public AnswerSource getAnswerSource() { return answerSource; }
    public void setAnswerSource(AnswerSource answerSource) { this.answerSource = answerSource; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getAnsweredAt() { return answeredAt; }
    public void setAnsweredAt(Instant answeredAt) { this.answeredAt = answeredAt; }
}
