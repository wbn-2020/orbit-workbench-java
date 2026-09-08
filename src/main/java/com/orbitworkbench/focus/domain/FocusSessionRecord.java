package com.orbitworkbench.focus.domain;

import java.time.Instant;

/** 专注时段写侧实体（v2 专注计时域）。 */
public class FocusSessionRecord {

    private Long id;
    private Long userId;
    private Instant startedAt;
    private int durationMinutes;
    private FocusMode mode;
    private String label;
    private String idempotencyKey;
    private int version;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
    public FocusMode getMode() { return mode; }
    public void setMode(FocusMode mode) { this.mode = mode; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
