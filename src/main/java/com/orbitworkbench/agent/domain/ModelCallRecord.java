package com.orbitworkbench.agent.domain;

import java.time.Instant;

public class ModelCallRecord {

    private Long id;
    private Long agentRunId;
    private String requestId;
    private Long connectionId;
    private Long modelProfileId;
    private String connectionNameSnapshot;
    private String modelNameSnapshot;
    private String protocol;
    private boolean streaming;
    private String status;
    private Instant startedAt;
    private Instant finishedAt;
    private Integer inputTokenCount;
    private Integer outputTokenCount;
    private Integer cachedTokenCount;
    private String providerRequestId;
    private String previousResponseId;
    private String errorCode;
    private String errorSummary;
    private int retryCount;
    private Instant createdAt;

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

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Long getConnectionId() {
        return connectionId;
    }

    public void setConnectionId(Long connectionId) {
        this.connectionId = connectionId;
    }

    public Long getModelProfileId() {
        return modelProfileId;
    }

    public void setModelProfileId(Long modelProfileId) {
        this.modelProfileId = modelProfileId;
    }

    public String getConnectionNameSnapshot() {
        return connectionNameSnapshot;
    }

    public void setConnectionNameSnapshot(String connectionNameSnapshot) {
        this.connectionNameSnapshot = connectionNameSnapshot;
    }

    public String getModelNameSnapshot() {
        return modelNameSnapshot;
    }

    public void setModelNameSnapshot(String modelNameSnapshot) {
        this.modelNameSnapshot = modelNameSnapshot;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public boolean isStreaming() {
        return streaming;
    }

    public void setStreaming(boolean streaming) {
        this.streaming = streaming;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public Integer getInputTokenCount() {
        return inputTokenCount;
    }

    public void setInputTokenCount(Integer inputTokenCount) {
        this.inputTokenCount = inputTokenCount;
    }

    public Integer getOutputTokenCount() {
        return outputTokenCount;
    }

    public void setOutputTokenCount(Integer outputTokenCount) {
        this.outputTokenCount = outputTokenCount;
    }

    public Integer getCachedTokenCount() {
        return cachedTokenCount;
    }

    public void setCachedTokenCount(Integer cachedTokenCount) {
        this.cachedTokenCount = cachedTokenCount;
    }

    public String getProviderRequestId() {
        return providerRequestId;
    }

    public void setProviderRequestId(String providerRequestId) {
        this.providerRequestId = providerRequestId;
    }

    public String getPreviousResponseId() {
        return previousResponseId;
    }

    public void setPreviousResponseId(String previousResponseId) {
        this.previousResponseId = previousResponseId;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorSummary() {
        return errorSummary;
    }

    public void setErrorSummary(String errorSummary) {
        this.errorSummary = errorSummary;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
