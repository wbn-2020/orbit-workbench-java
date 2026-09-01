package com.orbitworkbench.practice.domain;

/** 掌握状态（V28 CHECK 锁定）。它始终是尝试历史的纯函数，见 `15` §6。 */
public enum MasteryStatus {
    NEW,
    LEARNING,
    MASTERED
}
