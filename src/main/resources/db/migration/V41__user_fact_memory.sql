-- V41：用户级记忆层（借鉴 EvoFlow 用户画像事实；主链第 0.5 环）
-- 背景：project_fact 只回答「这个项目是什么」，不回答「我是谁、我在做什么、我的偏好」。
-- EvoFlow 用会话后自动沉淀 + 置信度 + 分类（preference/knowledge/context/behavior/goal）
-- 管理用户画像；我们取其「分类 + 置信度 + 显式确认」机制，但沉淀刻意不自动跑——
-- 用户数据核心由用户决定何时沉淀（点击触发），AI 建议、用户确认后才进入注入链路。
--
-- 与 project_fact 的口径差异：这里没有「版本」概念，跨项目、跨会话长期有效；
-- confidence 语义沿用 0-100（明确声明 90+ / 强烈暗示 70-80 / 谨慎推断 50-60）。
-- 注入链路只读 CONFIRMED，AI_ANALYZED 仅是候选池，与项目「系统推断不得表述为用户亲自负责」同纪律。

ALTER TABLE ai_scenario_route
    DROP CONSTRAINT chk_ai_scenario_code,
    ADD CONSTRAINT chk_ai_scenario_code CHECK (
        scenario_code IN ('INTERVIEW_QUESTION', 'INTERVIEW_REPORT', 'PROJECT_FACT',
            'KNOWLEDGE_ANSWER', 'USER_FACT')
    );

-- ai_call_audit.scenario_code 没有 CHECK 约束（V27 只约束 status），无需变更。

CREATE TABLE user_fact (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    fact_type VARCHAR(24) NOT NULL,
    title VARCHAR(120) NOT NULL,
    content VARCHAR(800) NOT NULL,
    source VARCHAR(16) NOT NULL,
    confirmation_status VARCHAR(16) NOT NULL,
    confidence INT NULL,
    confirmed_at DATETIME(6) NULL,
    archived_reason VARCHAR(24) NULL,
    source_hint VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_user_fact_user_status (user_id, confirmation_status, id),
    CONSTRAINT fk_user_fact_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT chk_user_fact_type CHECK (
        fact_type IN ('PREFERENCE', 'KNOWLEDGE', 'CONTEXT', 'BEHAVIOR', 'GOAL', 'OTHER')
    ),
    CONSTRAINT chk_user_fact_source CHECK (source IN ('AI_SUGGESTED', 'USER_ENTERED')),
    CONSTRAINT chk_user_fact_status CHECK (confirmation_status IN ('ANALYZED', 'CONFIRMED', 'ARCHIVED')),
    CONSTRAINT chk_user_fact_confidence CHECK (confidence IS NULL OR (confidence BETWEEN 0 AND 100)),
    CONSTRAINT chk_user_fact_archived_reason CHECK (
        archived_reason IS NULL OR archived_reason IN ('SUPERSEDED', 'MANUAL')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
