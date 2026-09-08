package com.orbitworkbench.worklog.domain;

/** 工作记录的分类，取值与 {@code work_log.category} 的 CHECK 约束一致。 */
public enum WorkLogCategory {
    PROJECT,
    INCIDENT,
    DECISION,
    LEARNING,
    OTHER
}
