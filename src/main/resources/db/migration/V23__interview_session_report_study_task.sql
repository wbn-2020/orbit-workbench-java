CREATE TABLE interview_session (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    title VARCHAR(128) NOT NULL,
    topic_mode VARCHAR(32) NOT NULL,
    form VARCHAR(16) NOT NULL,
    round VARCHAR(16) NOT NULL,
    target_role VARCHAR(128) NULL,
    target_experience_band VARCHAR(32) NULL,
    question_limit INT NOT NULL,
    follow_up_limit INT NOT NULL,
    turn_limit INT NOT NULL,
    duration_limit_minutes INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    started_at DATETIME(6) NULL,
    ended_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_interview_session_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    KEY idx_interview_session_user_status (user_id, status, updated_at, id),
    CONSTRAINT chk_interview_session_form CHECK (form IN ('TRAINING', 'FORMAL')),
    CONSTRAINT chk_interview_session_topic CHECK (
        topic_mode IN ('ROTE', 'PROJECT_DEEP_DIVE', 'AI_TECH', 'CODE_REVIEW',
            'FULL_PROCESS', 'TRANSITION_TEACHING')
    ),
    CONSTRAINT chk_interview_session_round CHECK (round IN ('FIRST', 'SECOND', 'THIRD', 'CUSTOM')),
    CONSTRAINT chk_interview_session_status CHECK (
        status IN ('READY', 'RUNNING', 'PAUSED', 'USER_ENDED', 'COMPLETING',
            'COMPLETED', 'FAILED', 'CANCELLED')
    ),
    CONSTRAINT chk_interview_session_limits CHECK (
        question_limit > 0 AND follow_up_limit >= 0 AND turn_limit > 0 AND duration_limit_minutes > 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE interview_turn (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    session_id BIGINT UNSIGNED NOT NULL,
    turn_no INT NOT NULL,
    turn_type VARCHAR(16) NOT NULL,
    question TEXT NOT NULL,
    answer TEXT NULL,
    answer_source VARCHAR(32) NULL,
    created_at DATETIME(6) NOT NULL,
    answered_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_interview_turn_session FOREIGN KEY (session_id) REFERENCES interview_session(id),
    UNIQUE KEY uk_interview_turn_no (session_id, turn_no),
    KEY idx_interview_turn_session_no (session_id, turn_no),
    CONSTRAINT chk_interview_turn_type CHECK (turn_type IN ('MAIN', 'FOLLOW_UP')),
    CONSTRAINT chk_interview_turn_source CHECK (
        answer_source IS NULL OR answer_source IN
        ('INDEPENDENT', 'PROMPTED', 'AI_ASSISTED', 'AI_GENERATED', 'HISTORY_IMPORT')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE interview_report (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    session_id BIGINT UNSIGNED NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_score INT NULL,
    dimension_scores_json JSON NULL,
    hiring_recommendation VARCHAR(16) NULL,
    strengths_json JSON NULL,
    weaknesses_json JSON NULL,
    follow_up_findings_json JSON NULL,
    project_mastery_json JSON NULL,
    knowledge_gaps_json JSON NULL,
    study_suggestions_json JSON NULL,
    failure_reason VARCHAR(512) NULL,
    retry_count INT NOT NULL DEFAULT 0,
    generated_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_interview_report_session FOREIGN KEY (session_id) REFERENCES interview_session(id),
    UNIQUE KEY uk_interview_report_session (session_id),
    CONSTRAINT chk_interview_report_status CHECK (
        status IN ('REPORT_PENDING', 'REPORT_FAILED', 'REPORT_READY')
    ),
    CONSTRAINT chk_interview_report_score CHECK (
        total_score IS NULL OR (total_score BETWEEN 0 AND 100)
    ),
    CONSTRAINT chk_interview_report_recommendation CHECK (
        hiring_recommendation IS NULL OR
        hiring_recommendation IN ('STRONG_PASS', 'PASS', 'HOLD', 'FAIL')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE study_task (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    source_type VARCHAR(16) NOT NULL,
    source_id BIGINT UNSIGNED NULL,
    title VARCHAR(255) NOT NULL,
    topic VARCHAR(128) NULL,
    task_type VARCHAR(32) NULL,
    priority VARCHAR(16) NOT NULL,
    estimated_minutes INT NULL,
    due_date DATE NULL,
    status VARCHAR(16) NOT NULL,
    manual TINYINT(1) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_study_task_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    KEY idx_study_task_user_status (user_id, status, due_date, id),
    CONSTRAINT chk_study_task_source CHECK (source_type IN ('MANUAL', 'REPORT', 'WORKBENCH')),
    CONSTRAINT chk_study_task_priority CHECK (priority IN ('HIGH', 'MEDIUM', 'LOW')),
    CONSTRAINT chk_study_task_status CHECK (
        status IN ('PLANNED', 'IN_PROGRESS', 'COMPLETED', 'POSTPONED', 'SKIPPED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
