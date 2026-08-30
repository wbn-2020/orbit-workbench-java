CREATE TABLE knowledge_chunk (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    project_version_id BIGINT UNSIGNED NOT NULL,
    project_file_id BIGINT UNSIGNED NULL,
    relative_path VARCHAR(1024) NOT NULL,
    chunk_no INT NOT NULL,
    content MEDIUMTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_knowledge_chunk_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    KEY idx_knowledge_chunk_version (project_version_id, chunk_no),
    KEY idx_knowledge_chunk_user (user_id, project_version_id),
    FULLTEXT KEY ft_knowledge_chunk_content (content) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE project_fact (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    project_version_id BIGINT UNSIGNED NOT NULL,
    fact_type VARCHAR(32) NOT NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    source VARCHAR(16) NOT NULL,
    confirmation_status VARCHAR(16) NOT NULL,
    confidence INT NULL,
    confirmed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_project_fact_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    KEY idx_project_fact_version (project_version_id, confirmation_status, id),
    CONSTRAINT chk_project_fact_type CHECK (
        fact_type IN ('BUSINESS', 'STRUCTURE', 'RISK', 'RESPONSIBILITY', 'TECH_STACK', 'OTHER')
    ),
    CONSTRAINT chk_project_fact_source CHECK (source IN ('AI_ANALYZED', 'USER_CONFIRMED')),
    CONSTRAINT chk_project_fact_status CHECK (confirmation_status IN ('ANALYZED', 'CONFIRMED', 'ARCHIVED')),
    CONSTRAINT chk_project_fact_confidence CHECK (confidence IS NULL OR (confidence BETWEEN 0 AND 100))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
