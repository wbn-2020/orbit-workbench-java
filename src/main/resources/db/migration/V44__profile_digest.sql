-- V44：画像编译快照（借鉴 EvoFlow 两阶段记忆固化的 Phase 2 编译思想）
-- 背景：V41/V43 的注入链路把 CONFIRMED 事实逐条拼接，事实一多就冗余膨胀——
-- 同一主题多条事实各占一行，模型拿到的画像越来越肥却信息密度不变。
-- EvoFlow 的答案是定期把散碎事实「编译」成一份高密度 standing profile（≤350 词、
-- 只保留会改变未来行为的高信号内容、保守推断、认知诚实）。
-- 我们取其编译纪律，但刻意手动触发（与蒸馏同一产品性格）：用户点「编译画像」才调 AI；
-- 快照过期（编译后又有新确认/归档）时注入自动回退逐条事实模式，绝不注入过时快照。

ALTER TABLE ai_scenario_route
    DROP CONSTRAINT chk_ai_scenario_code,
    ADD CONSTRAINT chk_ai_scenario_code CHECK (
        scenario_code IN ('INTERVIEW_QUESTION', 'INTERVIEW_REPORT', 'PROJECT_FACT',
            'KNOWLEDGE_ANSWER', 'USER_FACT', 'PROFILE_DIGEST')
    );

-- 每用户一行（UNIQUE），重编译即覆盖；被编译掉的事实本体不动，可随时回退逐条注入。
CREATE TABLE user_profile_digest (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    digest VARCHAR(2000) NOT NULL,
    -- 编译时刻 CONFIRMED 事实 id 的 JSON 数组：快照时效判定靠它与当前集合做差，
    -- 不靠时间戳——复查（reaffirm）不改 id/内容，不算使快照过期。
    source_fact_ids VARCHAR(1024) NOT NULL,
    source_count INT NOT NULL,
    model VARCHAR(120) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_profile_digest_user (user_id),
    CONSTRAINT fk_user_profile_digest_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT chk_user_profile_digest_count CHECK (source_count BETWEEN 1 AND 60)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
