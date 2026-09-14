package com.orbitworkbench.userfact.domain;

import java.time.Instant;

/**
 * 近期关注（V46，借鉴 EvoFlow 记忆结构的 topOfMind）：
 * 「最近在关注什么」的短周期信号，与长期画像事实分开存放。
 *
 * <p>带可选失效时刻——短期信号不该长期占位：过期即不再注入，面板提示更新。
 */
public class UserFocusNoteRecord {

    private Long id;
    private Long userId;
    private String content;
    /** 可选失效时刻；为空表示不自动失效。 */
    private Instant expiresAt;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
