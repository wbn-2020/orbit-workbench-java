package com.orbitworkbench.workbench.domain;

import java.time.Instant;

/** 复盘用学习目标行：updated_at 在 ACTIVE→DONE 唯一状态变更时写入，是完成时间代理。 */
public class LearningMetricRow extends WindowRow {

    private String status;
    private Instant updatedAt;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
