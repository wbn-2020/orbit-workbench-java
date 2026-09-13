-- V43：记忆时效 + 数据保留策略（借鉴 EvoFlow 的记忆衰减与 data_retention）。
--
-- 背景一：user_fact 只有「确认时刻」，没有「最近一次确认它仍然成立」的时间。
-- 三个月前确认的「Java 水平仅具备工作能力」今天仍会被原样注入出题与问答上下文，
-- 哪怕用户早已成长——记忆没有时效，注入的就是过期情报。
-- last_seen_at 的语义刻意收窄为「用户最后一次确认它仍然成立的时间」：
-- 注入本身不更新它，否则注入越频繁越显「新鲜」，衰减就永远不触发。
ALTER TABLE user_fact
    ADD COLUMN last_seen_at DATETIME(6) NULL AFTER confirmed_at;

-- 存量 CONFIRMED 行回填：以 confirmed_at 作为唯一的已知确认时刻，
-- 不编造一个更近的时间（同「不伪造」纪律）。
UPDATE user_fact
    SET last_seen_at = confirmed_at
    WHERE confirmation_status = 'CONFIRMED' AND last_seen_at IS NULL;

-- 背景二：AGENTS.md 安全边界要求模型请求/响应落库须有「保留周期」，
-- 此前只有脱敏、截断、删除，没有周期。这里把保留期做成用户可配的偏好。
-- NULL = 永久保留（默认，不悄悄删用户数据）；下限 7 天，避免配成 1 天把刚产生的账目立刻抹掉。
ALTER TABLE user_preference
    ADD COLUMN audit_retention_days INT NULL AFTER timezone_id,
    ADD COLUMN notification_retention_days INT NULL AFTER audit_retention_days,
    ADD CONSTRAINT chk_pref_audit_retention CHECK (
        audit_retention_days IS NULL OR audit_retention_days BETWEEN 7 AND 3650
    ),
    ADD CONSTRAINT chk_pref_notification_retention CHECK (
        notification_retention_days IS NULL OR notification_retention_days BETWEEN 7 AND 3650
    );
