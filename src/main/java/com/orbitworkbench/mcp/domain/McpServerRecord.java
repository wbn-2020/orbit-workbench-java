package com.orbitworkbench.mcp.domain;

import java.time.Instant;

public class McpServerRecord {

    private Long id;
    private Long workspaceId;
    private String serverCode;
    private String name;
    private String transport;
    private String endpointUrl;
    private String credentialRef;
    private boolean allowPrivateNetwork;
    private String status;
    private String syncStatus;
    private Instant lastSyncAt;
    private String lastErrorCode;
    private String lastErrorSummary;
    private Long lockVersion;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(Long workspaceId) {
        this.workspaceId = workspaceId;
    }

    public String getServerCode() {
        return serverCode;
    }

    public void setServerCode(String serverCode) {
        this.serverCode = serverCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTransport() {
        return transport;
    }

    public void setTransport(String transport) {
        this.transport = transport;
    }

    public String getEndpointUrl() {
        return endpointUrl;
    }

    public void setEndpointUrl(String endpointUrl) {
        this.endpointUrl = endpointUrl;
    }

    public String getCredentialRef() {
        return credentialRef;
    }

    public void setCredentialRef(String credentialRef) {
        this.credentialRef = credentialRef;
    }

    public boolean isAllowPrivateNetwork() {
        return allowPrivateNetwork;
    }

    public void setAllowPrivateNetwork(boolean allowPrivateNetwork) {
        this.allowPrivateNetwork = allowPrivateNetwork;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSyncStatus() {
        return syncStatus;
    }

    public void setSyncStatus(String syncStatus) {
        this.syncStatus = syncStatus;
    }

    public Instant getLastSyncAt() {
        return lastSyncAt;
    }

    public void setLastSyncAt(Instant lastSyncAt) {
        this.lastSyncAt = lastSyncAt;
    }

    public String getLastErrorCode() {
        return lastErrorCode;
    }

    public void setLastErrorCode(String lastErrorCode) {
        this.lastErrorCode = lastErrorCode;
    }

    public String getLastErrorSummary() {
        return lastErrorSummary;
    }

    public void setLastErrorSummary(String lastErrorSummary) {
        this.lastErrorSummary = lastErrorSummary;
    }

    public Long getLockVersion() {
        return lockVersion;
    }

    public void setLockVersion(Long lockVersion) {
        this.lockVersion = lockVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
