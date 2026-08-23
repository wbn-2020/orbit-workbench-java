ALTER TABLE document
    ADD UNIQUE KEY uk_document_id_workspace (id, workspace_id),
    DROP CHECK chk_document_parse_status,
    ADD CONSTRAINT chk_document_parse_status
        CHECK (parse_status IN ('UPLOADED', 'PARSING', 'READY', 'FAILED'));

ALTER TABLE storage_cleanup_failure
    DROP CHECK chk_storage_cleanup_resource_type,
    ADD CONSTRAINT chk_storage_cleanup_resource_type
        CHECK (resource_type IN (
            'DOCUMENT_UPLOAD', 'DATASET_UPLOAD', 'DATASET_PREVIEW',
            'DATASET_PROFILE', 'ARTIFACT_EXPORT'
        ));

CREATE TABLE dataset (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    workspace_id BIGINT UNSIGNED NOT NULL,
    document_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(255) NOT NULL,
    format VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    content_hash CHAR(64) NOT NULL,
    active_sheet_id BIGINT UNSIGNED NULL,
    row_count BIGINT NULL,
    column_count INT NULL,
    profile_version INT NOT NULL DEFAULT 0,
    version BIGINT UNSIGNED NOT NULL DEFAULT 1,
    error_code VARCHAR(64) NULL,
    error_summary VARCHAR(512) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    active_content_hash CHAR(64)
        GENERATED ALWAYS AS (
            CASE WHEN deleted_at IS NULL THEN content_hash ELSE NULL END
        ) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_dataset_id_workspace (id, workspace_id),
    UNIQUE KEY uk_dataset_document (document_id),
    UNIQUE KEY uk_dataset_active_hash (workspace_id, active_content_hash),
    KEY idx_dataset_workspace_status_updated (workspace_id, status, updated_at, id),
    CONSTRAINT fk_dataset_workspace FOREIGN KEY (workspace_id) REFERENCES workspace(id),
    CONSTRAINT fk_dataset_document_workspace FOREIGN KEY (document_id, workspace_id)
        REFERENCES document(id, workspace_id),
    CONSTRAINT chk_dataset_format CHECK (format IN ('CSV', 'XLSX')),
    CONSTRAINT chk_dataset_status
        CHECK (status IN ('UPLOADED', 'PARSING', 'READY', 'FAILED', 'DELETED')),
    CONSTRAINT chk_dataset_profile_version CHECK (profile_version >= 0),
    CONSTRAINT chk_dataset_version CHECK (version >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE dataset_sheet (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    dataset_id BIGINT UNSIGNED NOT NULL,
    sheet_index INT NOT NULL,
    sheet_name VARCHAR(255) NOT NULL,
    row_count BIGINT NOT NULL,
    column_count INT NOT NULL,
    preview_storage_ref VARCHAR(512) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_dataset_sheet_id_dataset (id, dataset_id),
    UNIQUE KEY uk_dataset_sheet_index (dataset_id, sheet_index),
    UNIQUE KEY uk_dataset_sheet_name (dataset_id, sheet_name),
    KEY idx_dataset_sheet_dataset_index (dataset_id, sheet_index, id),
    CONSTRAINT fk_dataset_sheet_dataset FOREIGN KEY (dataset_id) REFERENCES dataset(id),
    CONSTRAINT chk_dataset_sheet_index CHECK (sheet_index >= 0),
    CONSTRAINT chk_dataset_sheet_counts CHECK (row_count >= 0 AND column_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE dataset
    ADD CONSTRAINT fk_dataset_active_sheet
        FOREIGN KEY (active_sheet_id, id) REFERENCES dataset_sheet(id, dataset_id);

CREATE TABLE dataset_column (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    dataset_id BIGINT UNSIGNED NOT NULL,
    sheet_id BIGINT UNSIGNED NOT NULL,
    ordinal_position INT NOT NULL,
    column_name VARCHAR(255) NOT NULL,
    normalized_name VARCHAR(255) NOT NULL,
    inferred_type VARCHAR(32) NOT NULL,
    effective_type VARCHAR(32) NOT NULL,
    nullable TINYINT(1) NOT NULL,
    sample_values_json JSON NOT NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_dataset_column_position (sheet_id, ordinal_position),
    KEY idx_dataset_column_sheet_position (sheet_id, ordinal_position, id),
    CONSTRAINT fk_dataset_column_dataset FOREIGN KEY (dataset_id) REFERENCES dataset(id),
    CONSTRAINT fk_dataset_column_sheet_dataset FOREIGN KEY (sheet_id, dataset_id)
        REFERENCES dataset_sheet(id, dataset_id),
    CONSTRAINT chk_dataset_column_position CHECK (ordinal_position >= 0),
    CONSTRAINT chk_dataset_column_inferred_type
        CHECK (inferred_type IN (
            'EMPTY', 'STRING', 'INTEGER', 'DECIMAL', 'BOOLEAN', 'DATETIME'
        )),
    CONSTRAINT chk_dataset_column_effective_type
        CHECK (effective_type IN (
            'EMPTY', 'STRING', 'INTEGER', 'DECIMAL', 'BOOLEAN', 'DATETIME'
        )),
    CONSTRAINT chk_dataset_column_version CHECK (version >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE dataset_profile (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    dataset_id BIGINT UNSIGNED NOT NULL,
    sheet_id BIGINT UNSIGNED NOT NULL,
    profile_version INT NOT NULL,
    row_count BIGINT NOT NULL,
    column_count INT NOT NULL,
    summary_json JSON NOT NULL,
    quality_json JSON NOT NULL,
    profile_storage_ref VARCHAR(512) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_dataset_profile_version
        (dataset_id, sheet_id, profile_version),
    KEY idx_dataset_profile_latest
        (dataset_id, sheet_id, profile_version, id),
    CONSTRAINT fk_dataset_profile_dataset FOREIGN KEY (dataset_id) REFERENCES dataset(id),
    CONSTRAINT fk_dataset_profile_sheet_dataset FOREIGN KEY (sheet_id, dataset_id)
        REFERENCES dataset_sheet(id, dataset_id),
    CONSTRAINT chk_dataset_profile_profile_version CHECK (profile_version >= 1),
    CONSTRAINT chk_dataset_profile_counts CHECK (row_count >= 0 AND column_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
