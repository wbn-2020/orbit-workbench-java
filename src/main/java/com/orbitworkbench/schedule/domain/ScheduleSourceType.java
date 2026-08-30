package com.orbitworkbench.schedule.domain;

/**
 * 日程来源类型，对应 schedule_event.source_type CHECK 约束。
 * 本切片自定义日程用 CUSTOM 落表；STUDY_TASK/INTERVIEW/APPLICATION 为聚合时实时派生（不落表）。
 */
public enum ScheduleSourceType {
    CUSTOM,
    STUDY_TASK,
    INTERVIEW,
    APPLICATION
}
