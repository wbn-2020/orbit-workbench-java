package com.orbitworkbench.userfact.application;

import com.orbitworkbench.userfact.api.UserFactDtos.FocusNoteResponse;
import com.orbitworkbench.userfact.domain.UserFocusNoteRecord;
import com.orbitworkbench.userfact.infrastructure.mapper.UserFocusNoteMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 近期关注（V46，借鉴 EvoFlow 记忆结构的 topOfMind）：
 * 「这周在准备什么」的短周期信号，与长期画像事实分开存放，带可选失效时刻。
 *
 * <p>过期即不再注入——短期信号不该长期占位；事实本体保留到用户手动清除或覆盖，
 * 面板提示「已过期」，用户可更新或删除（不静默删用户写的东西）。
 */
@Service
public class FocusNoteService {

    private static final int MAX_EXPIRES_DAYS = 365;

    private final UserFocusNoteMapper focusMapper;

    public FocusNoteService(UserFocusNoteMapper focusMapper) {
        this.focusMapper = focusMapper;
    }

    @Transactional(readOnly = true)
    public FocusNoteResponse current(Long userId) {
        UserFocusNoteRecord record = focusMapper.findByUser(userId);
        return record == null ? null : toResponse(record, Instant.now());
    }

    @Transactional
    public FocusNoteResponse save(Long userId, String content, Integer expiresInDays) {
        Instant now = Instant.now();
        UserFocusNoteRecord record = new UserFocusNoteRecord();
        record.setUserId(userId);
        record.setContent(content.trim());
        record.setExpiresAt(expiresAt(now, expiresInDays));
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        focusMapper.upsert(record);
        return toResponse(focusMapper.findByUser(userId), now);
    }

    @Transactional
    public void clear(Long userId) {
        focusMapper.deleteByUser(userId);
    }

    /**
     * 注入链路读取口：未过期时返回一行紧凑文案（带「截止」提示），否则返回 null。
     * 过期不注入但不删除——用户自己写的内容，由用户决定去留。
     */
    @Transactional(readOnly = true)
    public String freshFocusForInjection(Long userId) {
        UserFocusNoteRecord record = focusMapper.findByUser(userId);
        if (record == null || isExpired(record, Instant.now())) {
            return null;
        }
        StringBuilder text = new StringBuilder("近期关注（短期信号，非长期画像）：")
                .append(record.getContent());
        if (record.getExpiresAt() != null) {
            text.append("（关注截止 ").append(record.getExpiresAt().toString().substring(0, 10)).append("）");
        }
        return text.toString();
    }

    private static Instant expiresAt(Instant now, Integer expiresInDays) {
        if (expiresInDays == null) {
            return null;
        }
        int days = Math.max(1, Math.min(MAX_EXPIRES_DAYS, expiresInDays));
        return now.plus(days, ChronoUnit.DAYS);
    }

    private static boolean isExpired(UserFocusNoteRecord record, Instant now) {
        return record.getExpiresAt() != null && !record.getExpiresAt().isAfter(now);
    }

    private static FocusNoteResponse toResponse(UserFocusNoteRecord record, Instant now) {
        return new FocusNoteResponse(record.getId(), record.getContent(), record.getExpiresAt(),
                isExpired(record, now), record.getUpdatedAt());
    }
}
