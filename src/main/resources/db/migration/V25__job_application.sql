CREATE TABLE job_application (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    company VARCHAR(128) NOT NULL,
    role VARCHAR(128) NOT NULL,
    jd_summary TEXT NULL,
    source VARCHAR(64) NULL,
    apply_date DATE NULL,
    interview_date DATE NULL,
    stage VARCHAR(16) NOT NULL,
    result VARCHAR(32) NULL,
    salary_note VARCHAR(128) NULL,
    contact VARCHAR(128) NULL,
    note VARCHAR(512) NULL,
    archived TINYINT(1) NOT NULL DEFAULT 0,
    stage_changed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_job_application_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    KEY idx_job_application_user_stage (user_id, archived, stage, updated_at),
    CONSTRAINT chk_job_application_stage CHECK (
        stage IN ('WATCHING', 'APPLIED', 'WRITTEN_TEST', 'INTERVIEWING', 'HR', 'OFFER', 'CLOSED')
    ),
    CONSTRAINT chk_job_application_result CHECK (
        result IS NULL OR result IN ('PENDING', 'PASSED', 'REJECTED', 'WITHDRAWN')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE job_application_event (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    application_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    event_type VARCHAR(16) NOT NULL,
    from_stage VARCHAR(16) NULL,
    to_stage VARCHAR(16) NULL,
    detail VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_job_application_event_app FOREIGN KEY (application_id) REFERENCES job_application(id),
    KEY idx_job_application_event_app (application_id, created_at),
    CONSTRAINT chk_job_application_event_type CHECK (
        event_type IN ('CREATED', 'STAGE_CHANGED', 'ARCHIVED', 'NOTE')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
