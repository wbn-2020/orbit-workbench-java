package com.orbitworkbench.aiconnection.domain;

import java.time.Instant;

public class CallAuditRecord {

    private Long id;
    private Long userId;
    private String scenarioCode;
    private Long primaryConnectionId;
    private Long usedConnectionId;
    private boolean backupAttempted;
    private String status;
    private String errorCode;
    private Integer latencyMs;
    private int requestChars;
    private int responseChars;
    private Integer inputTokens;
    private Integer outputTokens;
    private java.math.BigDecimal costAmount;
    public Integer getInputTokens() { return inputTokens; }
    public void setInputTokens(Integer inputTokens) { this.inputTokens = inputTokens; }
    public Integer getOutputTokens() { return outputTokens; }
    public void setOutputTokens(Integer outputTokens) { this.outputTokens = outputTokens; }
    public java.math.BigDecimal getCostAmount() { return costAmount; }
    public void setCostAmount(java.math.BigDecimal costAmount) { this.costAmount = costAmount; }

    private String configurationSnapshotJson;
    private Instant createdAt;
    private Instant finishedAt;
    /** 由 {@code ai_connection} 左连接带出，仅用于展示，不是本表列。 */
    private String usedConnectionName;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getScenarioCode() { return scenarioCode; }
    public void setScenarioCode(String scenarioCode) { this.scenarioCode = scenarioCode; }
    public Long getPrimaryConnectionId() { return primaryConnectionId; }
    public void setPrimaryConnectionId(Long primaryConnectionId) { this.primaryConnectionId = primaryConnectionId; }
    public Long getUsedConnectionId() { return usedConnectionId; }
    public void setUsedConnectionId(Long usedConnectionId) { this.usedConnectionId = usedConnectionId; }
    public boolean isBackupAttempted() { return backupAttempted; }
    public void setBackupAttempted(boolean backupAttempted) { this.backupAttempted = backupAttempted; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public Integer getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Integer latencyMs) { this.latencyMs = latencyMs; }
    public int getRequestChars() { return requestChars; }
    public void setRequestChars(int requestChars) { this.requestChars = requestChars; }
    public int getResponseChars() { return responseChars; }
    public void setResponseChars(int responseChars) { this.responseChars = responseChars; }
    public String getConfigurationSnapshotJson() { return configurationSnapshotJson; }
    public void setConfigurationSnapshotJson(String configurationSnapshotJson) {
        this.configurationSnapshotJson = configurationSnapshotJson;
    }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public String getUsedConnectionName() { return usedConnectionName; }
    public void setUsedConnectionName(String usedConnectionName) {
        this.usedConnectionName = usedConnectionName;
    }
}
