-- P3-M5: structured long-term Memory records and user-confirmable candidates.

CREATE TABLE memory_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    workspace_id BIGINT UNSIGNED NOT NULL,
    memory_type VARCHAR(32) NOT NULL,
    content_json JSON NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_id BIGINT UNSIGNED NULL,
    confidence DECIMAL(5,4) NOT NULL DEFAULT 1.0000,
    expires_at DATETIME(6) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'CONFIRMED',
    version BIGINT UNSIGNED NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_memory_record_workspace_status (workspace_id, status, updated_at, id),
    KEY idx_memory_record_workspace_type (workspace_id, memory_type, id),
    KEY idx_memory_record_source (source_type, source_id, id),
    CONSTRAINT fk_memory_record_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspace(id),
    CONSTRAINT chk_memory_record_status
        CHECK (status IN ('CONFIRMED', 'ARCHIVED', 'DELETED')),
    CONSTRAINT chk_memory_record_confidence
        CHECK (confidence >= 0.0000 AND confidence <= 1.0000),
    CONSTRAINT chk_memory_record_version CHECK (version >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE memory_candidate (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    workspace_id BIGINT UNSIGNED NOT NULL,
    memory_type VARCHAR(32) NOT NULL,
    content_json JSON NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_id BIGINT UNSIGNED NULL,
    confidence DECIMAL(5,4) NOT NULL DEFAULT 1.0000,
    expires_at DATETIME(6) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PROPOSED',
    version BIGINT UNSIGNED NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_memory_candidate_workspace_status (workspace_id, status, updated_at, id),
    KEY idx_memory_candidate_source (source_type, source_id, id),
    CONSTRAINT fk_memory_candidate_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspace(id),
    CONSTRAINT chk_memory_candidate_status
        CHECK (status IN ('PROPOSED', 'CONFIRMED', 'REJECTED', 'ARCHIVED', 'DELETED')),
    CONSTRAINT chk_memory_candidate_confidence
        CHECK (confidence >= 0.0000 AND confidence <= 1.0000),
    CONSTRAINT chk_memory_candidate_version CHECK (version >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
