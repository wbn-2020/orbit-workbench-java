-- 工作沉淀 / 学习更新 / 专注计时三模式的持久化底座（v2 产品闭环）。
-- 工作记录蒸馏出的知识卡片挂在 work_log 上，但不强制存在来源记录，允许独立沉淀。
-- focus_session 只记录已发生的专注/休息时段，统计由读侧按天聚合，不冗余落列。

CREATE TABLE work_log (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    category VARCHAR(32) NOT NULL,
    distilled TINYINT(1) NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_work_log_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    KEY idx_work_log_user_created (user_id, distilled, created_at, id),
    CONSTRAINT chk_work_log_category CHECK (
        category IN ('PROJECT', 'INCIDENT', 'DECISION', 'LEARNING', 'OTHER')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE knowledge_card (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    title VARCHAR(255) NOT NULL,
    summary TEXT NOT NULL,
    source_log_id BIGINT UNSIGNED NULL,
    tags_json VARCHAR(1024) NULL,
    version INT NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_knowledge_card_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_knowledge_card_source FOREIGN KEY (source_log_id) REFERENCES work_log(id),
    KEY idx_knowledge_card_user_created (user_id, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE learning_goal (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    title VARCHAR(255) NOT NULL,
    reason VARCHAR(1024) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    progress INT NOT NULL DEFAULT 0,
    linked_skill VARCHAR(128) NULL,
    version INT NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_learning_goal_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    KEY idx_learning_goal_user_status (user_id, status, updated_at, id),
    CONSTRAINT chk_learning_goal_status CHECK (
        status IN ('ACTIVE', 'PAUSED', 'DONE')
    ),
    CONSTRAINT chk_learning_goal_progress CHECK (
        progress BETWEEN 0 AND 100
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE focus_session (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    started_at DATETIME(6) NOT NULL,
    duration_minutes INT NOT NULL,
    mode VARCHAR(16) NOT NULL,
    label VARCHAR(255) NULL,
    version INT NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_focus_session_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    KEY idx_focus_session_user_started (user_id, started_at, id),
    CONSTRAINT chk_focus_session_mode CHECK (
        mode IN ('FOCUS', 'BREAK')
    ),
    CONSTRAINT chk_focus_session_duration CHECK (
        duration_minutes > 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
