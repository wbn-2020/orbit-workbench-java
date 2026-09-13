package com.orbitworkbench.aiconnection.domain;

import java.time.Instant;

public class AiConnectionRecord {
    private Long id;
    private Long providerCatalogId;
    private String providerType;
    private String name;
    private String baseUrl;
    private String endpointPath;
    private String protocol;
    private byte[] credentialCiphertext;
    private byte[] credentialIv;
    private Integer credentialKeyVersion;
    private String credentialMasked;
    private boolean enabled;
    private int timeoutMs;
    private java.math.BigDecimal inputPricePerMillion;
    private java.math.BigDecimal outputPricePerMillion;
    private java.math.BigDecimal cachedInputPricePerMillion;
    private String lastTestStatus;
    private Instant lastTestedAt;
    private Integer lastTestLatencyMs;
    private String lastErrorCode;
    private String lastErrorSummary;
    private long configurationVersion;
    private Instant deletedAt;
    private Long modelProfileId;
    private String modelName;
    private String modelDisplayName;
    private String supportedProtocols;
    private String capabilitiesJson;
    private String defaultParametersJson;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProviderCatalogId() { return providerCatalogId; }
    public void setProviderCatalogId(Long value) { this.providerCatalogId = value; }
    public String getProviderType() { return providerType; }
    public void setProviderType(String value) { this.providerType = value; }
    public String getName() { return name; }
    public void setName(String value) { this.name = value; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String value) { this.baseUrl = value; }
    public String getEndpointPath() { return endpointPath; }
    public void setEndpointPath(String value) { this.endpointPath = value; }
    public String getProtocol() { return protocol; }
    public void setProtocol(String value) { this.protocol = value; }
    public byte[] getCredentialCiphertext() { return credentialCiphertext; }
    public void setCredentialCiphertext(byte[] value) { this.credentialCiphertext = value; }
    public byte[] getCredentialIv() { return credentialIv; }
    public void setCredentialIv(byte[] value) { this.credentialIv = value; }
    public Integer getCredentialKeyVersion() { return credentialKeyVersion; }
    public void setCredentialKeyVersion(Integer value) { this.credentialKeyVersion = value; }
    public String getCredentialMasked() { return credentialMasked; }
    public void setCredentialMasked(String value) { this.credentialMasked = value; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { this.enabled = value; }
    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int value) { this.timeoutMs = value; }
    public java.math.BigDecimal getInputPricePerMillion() { return inputPricePerMillion; }
    public void setInputPricePerMillion(java.math.BigDecimal v) { this.inputPricePerMillion = v; }
    public java.math.BigDecimal getOutputPricePerMillion() { return outputPricePerMillion; }
    public void setOutputPricePerMillion(java.math.BigDecimal v) { this.outputPricePerMillion = v; }
    public java.math.BigDecimal getCachedInputPricePerMillion() { return cachedInputPricePerMillion; }
    public void setCachedInputPricePerMillion(java.math.BigDecimal v) { this.cachedInputPricePerMillion = v; }
    public String getLastTestStatus() { return lastTestStatus; }
    public void setLastTestStatus(String value) { this.lastTestStatus = value; }
    public Instant getLastTestedAt() { return lastTestedAt; }
    public void setLastTestedAt(Instant value) { this.lastTestedAt = value; }
    public Integer getLastTestLatencyMs() { return lastTestLatencyMs; }
    public void setLastTestLatencyMs(Integer value) { this.lastTestLatencyMs = value; }
    public String getLastErrorCode() { return lastErrorCode; }
    public void setLastErrorCode(String value) { this.lastErrorCode = value; }
    public String getLastErrorSummary() { return lastErrorSummary; }
    public void setLastErrorSummary(String value) { this.lastErrorSummary = value; }
    public long getConfigurationVersion() { return configurationVersion; }
    public void setConfigurationVersion(long value) { this.configurationVersion = value; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant value) { this.deletedAt = value; }
    public Long getModelProfileId() { return modelProfileId; }
    public void setModelProfileId(Long value) { this.modelProfileId = value; }
    public String getModelName() { return modelName; }
    public void setModelName(String value) { this.modelName = value; }
    public String getModelDisplayName() { return modelDisplayName; }
    public void setModelDisplayName(String value) { this.modelDisplayName = value; }
    public String getSupportedProtocols() { return supportedProtocols; }
    public void setSupportedProtocols(String value) { this.supportedProtocols = value; }
    public String getCapabilitiesJson() { return capabilitiesJson; }
    public void setCapabilitiesJson(String value) { this.capabilitiesJson = value; }
    public String getDefaultParametersJson() { return defaultParametersJson; }
    public void setDefaultParametersJson(String value) { this.defaultParametersJson = value; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant value) { this.createdAt = value; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant value) { this.updatedAt = value; }
}
