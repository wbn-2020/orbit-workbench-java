package com.orbitworkbench.studyplan.domain;

/** 复习任务来源。CRAFT（V49）= 本事库方法论转出的练习任务；GOAL（V58）= 学习目标拆出的执行步骤。 */
public enum StudyTaskSource {
    MANUAL,
    REPORT,
    WORKBENCH,
    CRAFT,
    GOAL
}
