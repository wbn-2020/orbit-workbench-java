package com.orbitworkbench.schedule.domain;

/** 日程状态，对应 schedule_event.status CHECK 约束。取消用 CANCELLED（表无软删列）。 */
public enum ScheduleStatus {
    PLANNED,
    COMPLETED,
    CANCELLED
}
