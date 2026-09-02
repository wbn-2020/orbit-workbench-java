package com.orbitworkbench.notification.domain;

/**
 * 通知事件类型（写入 notification.event_type，VARCHAR(32)）。
 * MyBatis 以枚举 name() 与列值互转。
 */
public enum NotificationEvent {
    INTERVIEW_REPORT_READY,
    INTERVIEW_REPORT_FAILED,
    KNOWLEDGE_BUILD_FAILED,
    PROJECT_IMPORT_PARTIAL,
    STUDY_TASK_DUE,
    SCHEDULE_REMINDER
}
