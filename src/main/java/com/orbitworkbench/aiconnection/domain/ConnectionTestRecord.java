package com.orbitworkbench.aiconnection.domain;

import java.time.Instant;

public class ConnectionTestRecord {
    private Long id;
    private Long connectionId;
    private String protocol;
    private String modelName;
    private boolean streaming;
    private String status;
    private Integer httpStatus;
    private Integer latencyMs;
    private int eventCount;
    private boolean doneMarkerReceived;
    private String errorCode;
    private String errorSummary;
    private String capabilitiesJson;
    private long configurationVersion;
    private Instant testedAt;
    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public Long getConnectionId() { return connectionId; }
    public void setConnectionId(Long value) { this.connectionId = value; }
    public String getProtocol() { return protocol; }
    public void setProtocol(String value) { this.protocol = value; }
    public String getModelName() { return modelName; }
    public void setModelName(String value) { this.modelName = value; }
    public boolean isStreaming() { return streaming; }
    public void setStreaming(boolean value) { this.streaming = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }
    public Integer getHttpStatus() { return httpStatus; }
    public void setHttpStatus(Integer value) { this.httpStatus = value; }
    public Integer getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Integer value) { this.latencyMs = value; }
    public int getEventCount() { return eventCount; }
    public void setEventCount(int value) { this.eventCount = value; }
    public boolean isDoneMarkerReceived() { return doneMarkerReceived; }
    public void setDoneMarkerReceived(boolean value) { this.doneMarkerReceived = value; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String value) { this.errorCode = value; }
    public String getErrorSummary() { return errorSummary; }
    public void setErrorSummary(String value) { this.errorSummary = value; }
    public String getCapabilitiesJson() { return capabilitiesJson; }
    public void setCapabilitiesJson(String value) { this.capabilitiesJson = value; }
    public long getConfigurationVersion() { return configurationVersion; }
    public void setConfigurationVersion(long value) { this.configurationVersion = value; }
    public Instant getTestedAt() { return testedAt; }
    public void setTestedAt(Instant value) { this.testedAt = value; }
}
