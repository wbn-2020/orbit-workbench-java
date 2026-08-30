CREATE TABLE job_profile (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    target_role VARCHAR(128) NOT NULL,
    target_experience_band VARCHAR(32) NOT NULL,
    career_stage VARCHAR(32) NOT NULL,
    target_level VARCHAR(32) NULL,
    target_company VARCHAR(128) NULL,
    java_skill_level VARCHAR(32) NOT NULL,
    ai_skill_level VARCHAR(32) NOT NULL,
    target_interview_date DATE NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_job_profile_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    UNIQUE KEY uk_job_profile_user (user_id),
    CONSTRAINT chk_job_profile_experience_band CHECK (
        target_experience_band IN ('GRADUATE', 'ONE_TO_THREE_YEARS',
            'THREE_TO_FIVE_YEARS', 'FIVE_PLUS_YEARS', 'CUSTOM')
    ),
    CONSTRAINT chk_job_profile_career_stage CHECK (
        career_stage IN ('GRADUATE', 'CAREER_TRANSITION', 'JOB_CHANGE')
    ),
    CONSTRAINT chk_job_profile_java_skill CHECK (
        java_skill_level IN ('BEGINNER', 'WORKING_KNOWLEDGE', 'PRACTICAL', 'ADVANCED')
    ),
    CONSTRAINT chk_job_profile_ai_skill CHECK (
        ai_skill_level IN ('BEGINNER', 'WORKING_KNOWLEDGE', 'PRACTICAL', 'ADVANCED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
