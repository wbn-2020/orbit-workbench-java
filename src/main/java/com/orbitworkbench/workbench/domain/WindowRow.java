package com.orbitworkbench.workbench.domain;

import java.time.Instant;

/** 复盘窗口的原始行（SQL 已按窗口过滤，Java 侧只做本地日期归组）。 */
public abstract class WindowRow {

    private Long id;
    private Instant createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
