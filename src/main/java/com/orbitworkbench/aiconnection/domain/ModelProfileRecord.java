package com.orbitworkbench.aiconnection.domain;

import java.time.Instant;

public class ModelProfileRecord {

    private Long id;
    private Long connectionId;
    private String modelName;
    private String displayName;
    private String supportedProtocolsJson;
    private String capabilitiesJson;
    private String defaultParametersJson;
    private boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getConnectionId() {
        return connectionId;
    }

    public void setConnectionId(Long connectionId) {
        this.connectionId = connectionId;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getSupportedProtocolsJson() {
        return supportedProtocolsJson;
    }

    public void setSupportedProtocolsJson(String supportedProtocolsJson) {
        this.supportedProtocolsJson = supportedProtocolsJson;
    }

    public String getCapabilitiesJson() {
        return capabilitiesJson;
    }

    public void setCapabilitiesJson(String capabilitiesJson) {
        this.capabilitiesJson = capabilitiesJson;
    }

    public String getDefaultParametersJson() {
        return defaultParametersJson;
    }

    public void setDefaultParametersJson(String defaultParametersJson) {
        this.defaultParametersJson = defaultParametersJson;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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
