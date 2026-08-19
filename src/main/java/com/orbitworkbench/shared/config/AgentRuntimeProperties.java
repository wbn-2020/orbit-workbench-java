package com.orbitworkbench.shared.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("orbit.agent.runtime")
public class AgentRuntimeProperties {

    private int maxOutputCharacters = 262_144;
    private int maxOutputBytes = 1_048_576;
    private int maxStreamEvents = 10_000;
    private Duration maxRunDuration = Duration.ofMinutes(5);
    private Duration heartbeatInterval = Duration.ofSeconds(10);

    public int getMaxOutputCharacters() {
        return maxOutputCharacters;
    }

    public void setMaxOutputCharacters(int maxOutputCharacters) {
        this.maxOutputCharacters = maxOutputCharacters;
    }

    public int getMaxOutputBytes() {
        return maxOutputBytes;
    }

    public void setMaxOutputBytes(int maxOutputBytes) {
        this.maxOutputBytes = maxOutputBytes;
    }

    public int getMaxStreamEvents() {
        return maxStreamEvents;
    }

    public void setMaxStreamEvents(int maxStreamEvents) {
        this.maxStreamEvents = maxStreamEvents;
    }

    public Duration getMaxRunDuration() {
        return maxRunDuration;
    }

    public void setMaxRunDuration(Duration maxRunDuration) {
        this.maxRunDuration = maxRunDuration;
    }

    public Duration getHeartbeatInterval() {
        return heartbeatInterval;
    }

    public void setHeartbeatInterval(Duration heartbeatInterval) {
        this.heartbeatInterval = heartbeatInterval;
    }
}
