package com.orbitworkbench.shared.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("orbit.tool.runtime")
public class ToolRuntimeProperties {

    private int maxSteps = 8;
    private int maxToolCalls = 20;
    private int maxArgumentsBytes = 32 * 1024;
    private int maxResultBytes = 256 * 1024;
    private int maxContextBytes = 1024 * 1024;
    private Duration defaultTimeout = Duration.ofSeconds(30);

    public int getMaxSteps() {
        return maxSteps;
    }

    public void setMaxSteps(int maxSteps) {
        this.maxSteps = maxSteps;
    }

    public int getMaxToolCalls() {
        return maxToolCalls;
    }

    public void setMaxToolCalls(int maxToolCalls) {
        this.maxToolCalls = maxToolCalls;
    }

    public int getMaxArgumentsBytes() {
        return maxArgumentsBytes;
    }

    public void setMaxArgumentsBytes(int maxArgumentsBytes) {
        this.maxArgumentsBytes = maxArgumentsBytes;
    }

    public int getMaxResultBytes() {
        return maxResultBytes;
    }

    public void setMaxResultBytes(int maxResultBytes) {
        this.maxResultBytes = maxResultBytes;
    }

    public int getMaxContextBytes() {
        return maxContextBytes;
    }

    public void setMaxContextBytes(int maxContextBytes) {
        this.maxContextBytes = maxContextBytes;
    }

    public Duration getDefaultTimeout() {
        return defaultTimeout;
    }

    public void setDefaultTimeout(Duration defaultTimeout) {
        this.defaultTimeout = defaultTimeout;
    }
}
