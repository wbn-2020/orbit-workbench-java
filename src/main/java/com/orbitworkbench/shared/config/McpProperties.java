package com.orbitworkbench.shared.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("orbit.mcp")
public class McpProperties {

    private String protocolVersion = "2025-06-18";
    private int maxTools = 100;
    private int maxResponseBytes = 256 * 1024;
    private Duration requestTimeout = Duration.ofSeconds(30);

    public String getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(String protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public int getMaxTools() {
        return maxTools;
    }

    public void setMaxTools(int maxTools) {
        this.maxTools = maxTools;
    }

    public int getMaxResponseBytes() {
        return maxResponseBytes;
    }

    public void setMaxResponseBytes(int maxResponseBytes) {
        this.maxResponseBytes = maxResponseBytes;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }
}
