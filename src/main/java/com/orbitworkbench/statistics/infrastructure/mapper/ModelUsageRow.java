package com.orbitworkbench.statistics.infrastructure.mapper;

import java.time.LocalDate;

public class ModelUsageRow {

    private Long connectionId;
    private String connectionName;
    private Long modelProfileId;
    private String modelName;
    private LocalDate usageDate;
    private Long callCount;
    private Long successCount;
    private Long failureCount;
    private Long inputTokens;
    private Long outputTokens;
    private Long averageLatencyMs;

    public Long getConnectionId() {
        return connectionId;
    }

    public void setConnectionId(Long connectionId) {
        this.connectionId = connectionId;
    }

    public String getConnectionName() {
        return connectionName;
    }

    public void setConnectionName(String connectionName) {
        this.connectionName = connectionName;
    }

    public Long getModelProfileId() {
        return modelProfileId;
    }

    public void setModelProfileId(Long modelProfileId) {
        this.modelProfileId = modelProfileId;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public LocalDate getUsageDate() {
        return usageDate;
    }

    public void setUsageDate(LocalDate usageDate) {
        this.usageDate = usageDate;
    }

    public Long getCallCount() {
        return callCount;
    }

    public void setCallCount(Long callCount) {
        this.callCount = callCount;
    }

    public Long getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(Long successCount) {
        this.successCount = successCount;
    }

    public Long getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(Long failureCount) {
        this.failureCount = failureCount;
    }

    public Long getInputTokens() {
        return inputTokens;
    }

    public void setInputTokens(Long inputTokens) {
        this.inputTokens = inputTokens;
    }

    public Long getOutputTokens() {
        return outputTokens;
    }

    public void setOutputTokens(Long outputTokens) {
        this.outputTokens = outputTokens;
    }

    public Long getAverageLatencyMs() {
        return averageLatencyMs;
    }

    public void setAverageLatencyMs(Long averageLatencyMs) {
        this.averageLatencyMs = averageLatencyMs;
    }
}
