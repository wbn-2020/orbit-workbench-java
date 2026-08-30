CREATE TABLE resume_document (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    title VARCHAR(128) NOT NULL,
    active_version_id BIGINT UNSIGNED NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_resume_document_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    UNIQUE KEY uk_resume_document_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE resume_version (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    resume_id BIGINT UNSIGNED NOT NULL,
    version_number INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    sections_json JSON NOT NULL,
    source_snapshot_json JSON NULL,
    change_summary VARCHAR(255) NULL,
    pdf_storage_ref VARCHAR(512) NULL,
    pdf_generated_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_resume_version_document FOREIGN KEY (resume_id) REFERENCES resume_document(id),
    UNIQUE KEY uk_resume_version_number (resume_id, version_number),
    KEY idx_resume_version_resume_created (resume_id, created_at, id),
    CONSTRAINT chk_resume_version_status CHECK (status IN ('DRAFT', 'FINAL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE resume_document
    ADD CONSTRAINT fk_resume_document_active_version
    FOREIGN KEY (active_version_id) REFERENCES resume_version(id);

CREATE TABLE practice_item (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    source_type VARCHAR(16) NOT NULL,
    source_id BIGINT UNSIGNED NULL,
    topic VARCHAR(128) NOT NULL,
    question TEXT NOT NULL,
    reference_answer TEXT NULL,
    mastery_status VARCHAR(16) NOT NULL,
    next_review_date DATE NULL,
    archived TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_practice_item_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    KEY idx_practice_item_user_review (user_id, archived, mastery_status, next_review_date, id),
    CONSTRAINT chk_practice_item_source CHECK (
        source_type IN ('REPORT', 'INTERVIEW_TURN', 'MANUAL')
    ),
    CONSTRAINT chk_practice_item_mastery CHECK (
        mastery_status IN ('NEW', 'LEARNING', 'MASTERED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE practice_attempt (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    practice_item_id BIGINT UNSIGNED NOT NULL,
    answer TEXT NOT NULL,
    self_score INT NULL,
    result VARCHAR(16) NOT NULL,
    feedback VARCHAR(512) NULL,
    attempted_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_practice_attempt_item FOREIGN KEY (practice_item_id) REFERENCES practice_item(id),
    KEY idx_practice_attempt_item (practice_item_id, attempted_at, id),
    CONSTRAINT chk_practice_attempt_score CHECK (
        self_score IS NULL OR self_score BETWEEN 0 AND 100
    ),
    CONSTRAINT chk_practice_attempt_result CHECK (
        result IN ('RETRY', 'PARTIAL', 'PASSED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE job_posting (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    company VARCHAR(128) NOT NULL,
    title VARCHAR(128) NOT NULL,
    city VARCHAR(64) NULL,
    salary_note VARCHAR(128) NULL,
    source VARCHAR(64) NULL,
    active_version_id BIGINT UNSIGNED NULL,
    archived TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_job_posting_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    KEY idx_job_posting_user (user_id, archived, updated_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE job_posting_version (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    job_posting_id BIGINT UNSIGNED NOT NULL,
    version_number INT NOT NULL,
    jd_text MEDIUMTEXT NOT NULL,
    required_skills_json JSON NULL,
    rule_version VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_job_posting_version_posting FOREIGN KEY (job_posting_id) REFERENCES job_posting(id),
    UNIQUE KEY uk_job_posting_version_number (job_posting_id, version_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE job_posting
    ADD CONSTRAINT fk_job_posting_active_version
    FOREIGN KEY (active_version_id) REFERENCES job_posting_version(id);

CREATE TABLE job_match_result (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    job_posting_version_id BIGINT UNSIGNED NOT NULL,
    resume_version_id BIGINT UNSIGNED NULL,
    profile_snapshot_json JSON NULL,
    rule_version VARCHAR(32) NOT NULL,
    total_score INT NOT NULL,
    skill_score INT NOT NULL,
    experience_score INT NOT NULL,
    project_score INT NOT NULL,
    matched_json JSON NULL,
    gaps_json JSON NULL,
    confirmation_status VARCHAR(16) NOT NULL,
    confirmed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_job_match_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_job_match_posting_version FOREIGN KEY (job_posting_version_id) REFERENCES job_posting_version(id),
    CONSTRAINT fk_job_match_resume_version FOREIGN KEY (resume_version_id) REFERENCES resume_version(id),
    KEY idx_job_match_user_created (user_id, created_at, id),
    CONSTRAINT chk_job_match_scores CHECK (
        total_score BETWEEN 0 AND 100
        AND skill_score BETWEEN 0 AND 100
        AND experience_score BETWEEN 0 AND 100
        AND project_score BETWEEN 0 AND 100
    ),
    CONSTRAINT chk_job_match_confirmation CHECK (
        confirmation_status IN ('PENDING', 'CONFIRMED', 'REJECTED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE capability_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    capability_code VARCHAR(64) NOT NULL,
    capability_name VARCHAR(128) NOT NULL,
    category VARCHAR(64) NOT NULL,
    score INT NOT NULL,
    confidence INT NOT NULL,
    evidence_count INT NOT NULL,
    evidence_json JSON NOT NULL,
    calculated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_capability_record_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    UNIQUE KEY uk_capability_user_code (user_id, capability_code),
    CONSTRAINT chk_capability_scores CHECK (
        score BETWEEN 0 AND 100 AND confidence BETWEEN 0 AND 100 AND evidence_count >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
