CREATE TABLE document (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    workspace_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(255) NOT NULL,
    media_type VARCHAR(128) NOT NULL,
    size_bytes BIGINT NOT NULL,
    storage_ref VARCHAR(512) NOT NULL,
    content_hash CHAR(64) NOT NULL,
    parse_status VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_document_workspace FOREIGN KEY (workspace_id) REFERENCES workspace(id),
    UNIQUE KEY uk_document_workspace_hash (workspace_id, content_hash),
    KEY idx_document_workspace_created (workspace_id, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE task_document (
    task_id BIGINT UNSIGNED NOT NULL,
    document_id BIGINT UNSIGNED NOT NULL,
    relation_type VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (task_id, document_id, relation_type),
    CONSTRAINT fk_task_document_task FOREIGN KEY (task_id) REFERENCES task(id) ON DELETE CASCADE,
    CONSTRAINT fk_task_document_document FOREIGN KEY (document_id) REFERENCES document(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE artifact (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    workspace_id BIGINT UNSIGNED NOT NULL,
    task_id BIGINT UNSIGNED NOT NULL,
    source_run_id BIGINT UNSIGNED NOT NULL,
    artifact_type VARCHAR(64) NOT NULL,
    title VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    current_version_id BIGINT UNSIGNED NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_artifact_workspace FOREIGN KEY (workspace_id) REFERENCES workspace(id),
    CONSTRAINT fk_artifact_task_workspace FOREIGN KEY (task_id, workspace_id)
        REFERENCES task(id, workspace_id),
    CONSTRAINT fk_artifact_source_run_task FOREIGN KEY (source_run_id, task_id)
        REFERENCES agent_run(id, task_id),
    KEY idx_artifact_workspace_updated (workspace_id, updated_at, id),
    KEY idx_artifact_source_run (source_run_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE artifact_version (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    artifact_id BIGINT UNSIGNED NOT NULL,
    version_number INT NOT NULL,
    content_ref VARCHAR(512) NOT NULL,
    content_format VARCHAR(32) NOT NULL,
    source_run_id BIGINT UNSIGNED NULL,
    change_summary VARCHAR(512) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_artifact_version_id_artifact (id, artifact_id),
    CONSTRAINT fk_artifact_version_artifact FOREIGN KEY (artifact_id) REFERENCES artifact(id),
    CONSTRAINT fk_artifact_version_run FOREIGN KEY (source_run_id) REFERENCES agent_run(id),
    UNIQUE KEY uk_artifact_version_number (artifact_id, version_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE artifact
    ADD CONSTRAINT fk_artifact_current_version FOREIGN KEY (current_version_id, id)
        REFERENCES artifact_version(id, artifact_id);
