package com.orbitworkbench.agent.domain;

import java.time.Instant;

public class RunEventRecord {

    private Long id;
    private Long agentRunId;
    private Long modelCallId;
    private long sequence;
    private String eventType;
    private String eventSummary;
    private String payloadJson;
    private Instant occurredAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAgentRunId() {
        return agentRunId;
    }

    public void setAgentRunId(Long agentRunId) {
        this.agentRunId = agentRunId;
    }

    public Long getModelCallId() {
        return modelCallId;
    }

    public void setModelCallId(Long modelCallId) {
        this.modelCallId = modelCallId;
    }

    public long getSequence() {
        return sequence;
    }

    public void setSequence(long sequence) {
        this.sequence = sequence;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getEventSummary() {
        return eventSummary;
    }

    public void setEventSummary(String eventSummary) {
        this.eventSummary = eventSummary;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }
}

