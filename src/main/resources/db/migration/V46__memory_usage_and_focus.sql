-- V46：记忆使用治理 + 近期关注（借鉴 EvoFlow 资产中心「统计」与记忆 topOfMind）
-- 背景一（用量）：V45 已在调用审计快照里记录了注入的事实 id，但只能逐条翻审计，
-- 回答不了「我确认的 20 条事实，哪些真正在影响 AI」。加计数列，注入时自增——
-- 让「冷记忆」（确认了却从未被注入）浮出来，用户可以清理低价值事实。
-- 背景二（近期关注）：记忆不该只有「长期画像」。求职者「这周在准备某公司二面」是
-- 短周期信号，塞进长期事实会污染画像、过期还得手动删。独立一张小表，带可选失效时刻：
-- 过期即不再注入（短期信号不该长期占位），面板提示更新。
--
-- 计数语义如实：记的是「被组装进 AI 请求的记忆」次数（prompt 组装时自增），
-- 与 last_seen_at（用户确认时效）刻意分开——注入不该让事实显得「新鲜」（V43 纪律）。

ALTER TABLE user_fact
    ADD COLUMN injection_count INT NOT NULL DEFAULT 0 COMMENT '被组装进 AI 请求的次数（V46 用量治理）',
    ADD COLUMN last_injected_at DATETIME(6) NULL COMMENT '最后一次被注入的时间；从未注入为 NULL';

CREATE TABLE user_focus_note (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    content VARCHAR(400) NOT NULL,
    -- 可选失效时刻：为空表示不自动失效，由用户手动更新或清除
    expires_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_focus_note_user (user_id),
    CONSTRAINT fk_user_focus_note_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
