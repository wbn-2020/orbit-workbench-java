package com.orbitworkbench.workbench.domain;

import java.time.Instant;
import java.time.LocalDate;

/** 复盘用复习任务行：完成与否由 COMPLETED 状态表达，updated_at 为完成时间代理。 */
public class StudyMetricRow extends WindowRow {

    private String status;
    private LocalDate dueDate;
    private Instant updatedAt;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
