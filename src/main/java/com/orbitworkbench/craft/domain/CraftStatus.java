package com.orbitworkbench.craft.domain;

/**
 * 套路确认状态。AI 建议先进 ANALYZED 候选池，用户确认后进 CONFIRMED 库；
 * ARCHIVED 保留痕迹，不物理删除。
 */
public enum CraftStatus {
    ANALYZED,
    CONFIRMED,
    ARCHIVED
}
