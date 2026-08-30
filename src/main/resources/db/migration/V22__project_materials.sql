CREATE TABLE IF NOT EXISTS project (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    workspace_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_project_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_project_workspace FOREIGN KEY (workspace_id) REFERENCES workspace(id),
    KEY idx_project_user_updated (user_id, status, updated_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS project_version (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    project_id BIGINT UNSIGNED NOT NULL,
    version_number INT NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_file_name VARCHAR(255) NOT NULL,
    source_storage_ref VARCHAR(512) NOT NULL,
    source_content_hash CHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_file_count INT NOT NULL,
    parsed_file_count INT NOT NULL,
    excluded_file_count INT NOT NULL,
    failed_file_count INT NOT NULL,
    total_size_bytes BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_project_version_project FOREIGN KEY (project_id) REFERENCES project(id),
    UNIQUE KEY uk_project_version_number (project_id, version_number),
    KEY idx_project_version_project_created (project_id, created_at, id),
    CONSTRAINT chk_project_version_number CHECK (version_number > 0),
    CONSTRAINT chk_project_version_status CHECK (
        status IN ('REVIEW_REQUIRED', 'PARTIAL', 'PUBLISHED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS project_file (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    project_version_id BIGINT UNSIGNED NOT NULL,
    relative_path VARCHAR(1024) NOT NULL,
    relative_path_hash CHAR(64) NOT NULL,
    media_type VARCHAR(128) NULL,
    size_bytes BIGINT NOT NULL,
    storage_ref VARCHAR(512) NULL,
    content_hash CHAR(64) NULL,
    status VARCHAR(32) NOT NULL,
    status_reason VARCHAR(512) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_project_file_version FOREIGN KEY (project_version_id) REFERENCES project_version(id),
    UNIQUE KEY uk_project_file_version_path (project_version_id, relative_path_hash),
    KEY idx_project_file_version_status (project_version_id, status, id),
    CONSTRAINT chk_project_file_status CHECK (
        status IN ('PARSED', 'EXCLUDED', 'FAILED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
