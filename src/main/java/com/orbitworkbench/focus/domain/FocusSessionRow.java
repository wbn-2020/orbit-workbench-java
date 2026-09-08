package com.orbitworkbench.focus.domain;

import java.time.Instant;

/** 专注时段读侧投影。 */
public class FocusSessionRow {

    private Long id;
    private Instant startedAt;
    private int durationMinutes;
    private FocusMode mode;
    private String label;
    private Instant createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
    public FocusMode getMode() { return mode; }
    public void setMode(FocusMode mode) { this.mode = mode; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
