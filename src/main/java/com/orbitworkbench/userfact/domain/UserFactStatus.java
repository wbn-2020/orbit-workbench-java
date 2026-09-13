package com.orbitworkbench.userfact.domain;

/**
 * 用户事实确认状态。只有 CONFIRMED 会被注入 AI 上下文；
 * ARCHIVED 保留痕迹（SUPERSEDED 表示被新事实取代），不物理删除。
 */
public enum UserFactStatus {
    ANALYZED,
    CONFIRMED,
    ARCHIVED
}
