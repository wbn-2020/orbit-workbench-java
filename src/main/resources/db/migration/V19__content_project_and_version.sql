-- P3-M6: content projects, material associations and AgentRun-backed versions.

CREATE TABLE content_project (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    workspace_id BIGINT UNSIGNED NOT NULL,
    connection_id BIGINT UNSIGNED NOT NULL,
    title VARCHAR(120) NOT NULL,
    topic VARCHAR(2000) NOT NULL,
    audience VARCHAR(512) NULL,
    style VARCHAR(512) NULL,
    output_format VARCHAR(32) NOT NULL DEFAULT 'MARKDOWN',
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    version BIGINT UNSIGNED NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_content_project_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspace(id),
    CONSTRAINT fk_content_project_connection
        FOREIGN KEY (connection_id) REFERENCES ai_connection(id),
    KEY idx_content_project_workspace_status_updated (workspace_id, status, updated_at, id),
    CONSTRAINT chk_content_project_output_format
        CHECK (output_format IN ('MARKDOWN')),
    CONSTRAINT chk_content_project_status
        CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    CONSTRAINT chk_content_project_version CHECK (version >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE content_project_material (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    content_project_id BIGINT UNSIGNED NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_id BIGINT UNSIGNED NOT NULL,
    relation_type VARCHAR(32) NOT NULL DEFAULT 'REFERENCE',
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_content_project_material_project
        FOREIGN KEY (content_project_id) REFERENCES content_project(id) ON DELETE CASCADE,
    UNIQUE KEY uk_content_project_material_source
        (content_project_id, source_type, source_id),
    KEY idx_content_project_material_order
        (content_project_id, sort_order, id),
    CONSTRAINT chk_content_project_material_source
        CHECK (source_type IN ('DOCUMENT', 'ARTIFACT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE content_version (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    content_project_id BIGINT UNSIGNED NOT NULL,
    version_number INT UNSIGNED NOT NULL,
    operation VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'QUEUED',
    title VARCHAR(255) NOT NULL,
    content_format VARCHAR(32) NOT NULL DEFAULT 'MARKDOWN',
    request_key VARCHAR(128) NULL,
    task_id BIGINT UNSIGNED NULL,
    source_run_id BIGINT UNSIGNED NULL,
    artifact_id BIGINT UNSIGNED NULL,
    artifact_version_id BIGINT UNSIGNED NULL,
    error_code VARCHAR(64) NULL,
    error_summary VARCHAR(512) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_content_version_project
        FOREIGN KEY (content_project_id) REFERENCES content_project(id) ON DELETE CASCADE,
    CONSTRAINT fk_content_version_task
        FOREIGN KEY (task_id) REFERENCES task(id),
    CONSTRAINT fk_content_version_run
        FOREIGN KEY (source_run_id) REFERENCES agent_run(id),
    CONSTRAINT fk_content_version_artifact
        FOREIGN KEY (artifact_id) REFERENCES artifact(id),
    CONSTRAINT fk_content_version_artifact_version
        FOREIGN KEY (artifact_version_id, artifact_id)
        REFERENCES artifact_version(id, artifact_id),
    UNIQUE KEY uk_content_version_number (content_project_id, version_number),
    UNIQUE KEY uk_content_version_request (content_project_id, request_key),
    UNIQUE KEY uk_content_version_task (task_id),
    UNIQUE KEY uk_content_version_run (source_run_id),
    KEY idx_content_version_project_updated
        (content_project_id, updated_at, id),
    CONSTRAINT chk_content_version_operation
        CHECK (operation IN ('OUTLINE', 'DRAFT', 'REWRITE', 'EXPAND', 'COMPRESS', 'REVIEW')),
    CONSTRAINT chk_content_version_status
        CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'PAUSED', 'CANCELLED')),
    CONSTRAINT chk_content_version_format
        CHECK (content_format IN ('MARKDOWN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
