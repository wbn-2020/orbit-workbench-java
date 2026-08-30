package com.orbitworkbench.aiconnection.domain;

import java.time.Instant;

public class ScenarioRouteRecord {

    private Long id;
    private Long userId;
    private String scenarioCode;
    private Long primaryConnectionId;
    private Long backupConnectionId;
    private boolean failoverEnabled;
    private int version;
    private Instant createdAt;
    private Instant updatedAt;
    /** 以下两项由 {@code ai_connection} 左连接带出，仅用于展示，不是本表列。 */
    private String primaryConnectionName;
    private String backupConnectionName;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getScenarioCode() { return scenarioCode; }
    public void setScenarioCode(String scenarioCode) { this.scenarioCode = scenarioCode; }
    public Long getPrimaryConnectionId() { return primaryConnectionId; }
    public void setPrimaryConnectionId(Long primaryConnectionId) { this.primaryConnectionId = primaryConnectionId; }
    public Long getBackupConnectionId() { return backupConnectionId; }
    public void setBackupConnectionId(Long backupConnectionId) { this.backupConnectionId = backupConnectionId; }
    public boolean isFailoverEnabled() { return failoverEnabled; }
    public void setFailoverEnabled(boolean failoverEnabled) { this.failoverEnabled = failoverEnabled; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public String getPrimaryConnectionName() { return primaryConnectionName; }
    public void setPrimaryConnectionName(String primaryConnectionName) {
        this.primaryConnectionName = primaryConnectionName;
    }
    public String getBackupConnectionName() { return backupConnectionName; }
    public void setBackupConnectionName(String backupConnectionName) {
        this.backupConnectionName = backupConnectionName;
    }
}
