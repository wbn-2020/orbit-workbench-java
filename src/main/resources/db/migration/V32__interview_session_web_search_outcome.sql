-- Q-07b：把「本场出题实际联网了没有」固化成会话上的历史事实（ADR-0012）。
-- 刻意不在读取时派生：连接的联网形状是可改配置，事后改一次声明就会改写既有会话的过去，
-- 与 V30 给报告补 scoring_rule_version（不跨规则版本比分）要防的是同一类错误。
ALTER TABLE interview_session
    ADD COLUMN web_search_dialect VARCHAR(40) NULL AFTER web_search_policy,
    ADD COLUMN web_search_applied VARCHAR(16) NULL AFTER web_search_dialect,
    ADD COLUMN web_search_note VARCHAR(255) NULL AFTER web_search_applied,
    ADD CONSTRAINT chk_interview_session_web_search_applied CHECK (
        web_search_applied IS NULL
            OR web_search_applied IN ('NOT_REQUESTED', 'APPLIED', 'DEGRADED')
    );
