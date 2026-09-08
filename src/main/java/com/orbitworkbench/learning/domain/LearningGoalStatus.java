package com.orbitworkbench.learning.domain;

/** 学习目标状态，取值与 {@code learning_goal.status} 的 CHECK 约束一致。 */
public enum LearningGoalStatus {
    ACTIVE,
    PAUSED,
    DONE
}
