package com.orbitworkbench.interview.domain;

import java.io.Serializable;

/** 一场会话里各回答来源的题数，用于报告中心展示独立性差异（14 §3、§7）。 */
public class AnswerSourceCount implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long sessionId;
    private String answerSource;
    private long itemCount;

    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public String getAnswerSource() { return answerSource; }
    public void setAnswerSource(String answerSource) { this.answerSource = answerSource; }
    public long getItemCount() { return itemCount; }
    public void setItemCount(long itemCount) { this.itemCount = itemCount; }
}
