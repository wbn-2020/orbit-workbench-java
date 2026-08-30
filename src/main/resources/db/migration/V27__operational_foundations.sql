ALTER TABLE project_version
    ADD COLUMN knowledge_build_status VARCHAR(16) NOT NULL DEFAULT 'PENDING' AFTER status,
    ADD COLUMN knowledge_chunk_count INT NOT NULL DEFAULT 0 AFTER knowledge_build_status,
    ADD COLUMN knowledge_build_attempts INT NOT NULL DEFAULT 0 AFTER knowledge_chunk_count,
    ADD COLUMN knowledge_build_error VARCHAR(512) NULL AFTER knowledge_build_attempts,
    ADD COLUMN knowledge_built_at DATETIME(6) NULL AFTER knowledge_build_error,
    ADD CONSTRAINT chk_project_version_knowledge_status CHECK (
        knowledge_build_status IN ('PENDING', 'BUILDING', 'READY', 'FAILED')
    );

CREATE TABLE interviewer_profile (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NULL,
    code VARCHAR(64) NULL,
    name VARCHAR(64) NOT NULL,
    description VARCHAR(512) NULL,
    system_prompt TEXT NOT NULL,
    topic_mode VARCHAR(32) NOT NULL,
    focus_tags_json JSON NULL,
    default_question_limit INT NOT NULL,
    default_follow_up_limit INT NOT NULL,
    built_in TINYINT(1) NOT NULL DEFAULT 0,
    archived TINYINT(1) NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_interviewer_profile_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    UNIQUE KEY uk_interviewer_profile_code (code),
    KEY idx_interviewer_profile_user (user_id, archived, updated_at, id),
    CONSTRAINT chk_interviewer_profile_topic CHECK (
        topic_mode IN ('ROTE', 'PROJECT_DEEP_DIVE', 'AI_TECH', 'CODE_REVIEW',
            'FULL_PROCESS', 'TRANSITION_TEACHING')
    ),
    CONSTRAINT chk_interviewer_profile_limits CHECK (
        default_question_limit > 0 AND default_follow_up_limit >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO interviewer_profile (
    user_id, code, name, description, system_prompt, topic_mode, focus_tags_json,
    default_question_limit, default_follow_up_limit, built_in, archived, version,
    created_at, updated_at
) VALUES
    (NULL, 'JAVA_FOUNDATION', 'Java 基础面试官',
     '围绕 Java、JVM、并发、数据库和中间件检验基础与原理。',
     '你是一名严格但克制的 Java 后端面试官。问题应准确、可追问，并关注原理、边界和实践证据。',
     'ROTE', JSON_ARRAY('Java', 'JVM', '并发', 'MySQL', 'Redis'), 8, 3, 1, 0, 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (NULL, 'PROJECT_DEEP_DIVE', '项目深挖面试官',
     '基于已确认项目事实追问业务、架构、取舍、风险和个人职责。',
     '你是一名项目深挖面试官。只能依据会话项目快照提问，优先验证候选人的真实职责、技术取舍与异常处理。',
     'PROJECT_DEEP_DIVE', JSON_ARRAY('项目事实', '架构取舍', '故障恢复', '个人职责'), 6, 5, 1, 0, 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (NULL, 'AI_APPLICATION', 'Java + AI 应用面试官',
     '覆盖模型接入、RAG、结构化输出、评测、安全和工程化。',
     '你是一名 Java + AI 应用工程面试官。问题应同时覆盖模型能力边界、后端工程实现、评测与安全。',
     'AI_TECH', JSON_ARRAY('LLM', 'RAG', 'Prompt', '评测', '安全'), 7, 4, 1, 0, 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

ALTER TABLE interview_session
    ADD COLUMN interviewer_id BIGINT UNSIGNED NULL AFTER round,
    ADD COLUMN interviewer_snapshot_json JSON NULL AFTER interviewer_name_snapshot,
    ADD COLUMN scheduled_at DATETIME(6) NULL AFTER duration_limit_minutes,
    ADD CONSTRAINT fk_interview_session_interviewer FOREIGN KEY (interviewer_id) REFERENCES interviewer_profile(id);

CREATE TABLE notification (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    title VARCHAR(128) NOT NULL,
    content VARCHAR(512) NOT NULL,
    resource_type VARCHAR(32) NULL,
    resource_id BIGINT UNSIGNED NULL,
    resource_route VARCHAR(255) NULL,
    idempotency_key VARCHAR(160) NULL,
    read_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    UNIQUE KEY uk_notification_user_key (user_id, idempotency_key),
    KEY idx_notification_user_read (user_id, read_at, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE schedule_event (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_id BIGINT UNSIGNED NULL,
    title VARCHAR(255) NOT NULL,
    start_at DATETIME(6) NOT NULL,
    end_at DATETIME(6) NULL,
    all_day TINYINT(1) NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL,
    reminder_minutes INT NULL,
    resource_route VARCHAR(255) NULL,
    note VARCHAR(512) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_schedule_event_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    UNIQUE KEY uk_schedule_event_source (user_id, source_type, source_id),
    KEY idx_schedule_event_user_start (user_id, status, start_at, id),
    CONSTRAINT chk_schedule_event_source CHECK (
        source_type IN ('CUSTOM', 'STUDY_TASK', 'INTERVIEW', 'APPLICATION')
    ),
    CONSTRAINT chk_schedule_event_status CHECK (
        status IN ('PLANNED', 'COMPLETED', 'CANCELLED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_preference (
    user_id BIGINT UNSIGNED NOT NULL,
    notify_report_ready TINYINT(1) NOT NULL DEFAULT 1,
    notify_study_due TINYINT(1) NOT NULL DEFAULT 1,
    notify_interview TINYINT(1) NOT NULL DEFAULT 1,
    notify_import_failure TINYINT(1) NOT NULL DEFAULT 1,
    notify_ai_failure TINYINT(1) NOT NULL DEFAULT 1,
    timezone_id VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai',
    version INT NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_user_preference_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ai_scenario_route (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    scenario_code VARCHAR(32) NOT NULL,
    primary_connection_id BIGINT UNSIGNED NOT NULL,
    backup_connection_id BIGINT UNSIGNED NULL,
    failover_enabled TINYINT(1) NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ai_scenario_route_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_ai_scenario_primary FOREIGN KEY (primary_connection_id) REFERENCES ai_connection(id),
    CONSTRAINT fk_ai_scenario_backup FOREIGN KEY (backup_connection_id) REFERENCES ai_connection(id),
    UNIQUE KEY uk_ai_scenario_user_code (user_id, scenario_code),
    CONSTRAINT chk_ai_scenario_code CHECK (
        scenario_code IN ('INTERVIEW_QUESTION', 'INTERVIEW_REPORT', 'PROJECT_FACT', 'KNOWLEDGE_ANSWER')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ai_call_audit (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    scenario_code VARCHAR(32) NOT NULL,
    primary_connection_id BIGINT UNSIGNED NOT NULL,
    used_connection_id BIGINT UNSIGNED NOT NULL,
    backup_attempted TINYINT(1) NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL,
    error_code VARCHAR(64) NULL,
    latency_ms INT NULL,
    request_chars INT NOT NULL DEFAULT 0,
    response_chars INT NOT NULL DEFAULT 0,
    configuration_snapshot_json JSON NULL,
    created_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ai_call_audit_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    KEY idx_ai_call_audit_user_created (user_id, created_at, id),
    CONSTRAINT chk_ai_call_audit_status CHECK (
        status IN ('RUNNING', 'SUCCEEDED', 'FAILED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
