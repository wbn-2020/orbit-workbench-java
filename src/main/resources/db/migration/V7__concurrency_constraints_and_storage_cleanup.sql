ALTER TABLE ai_connection
    ADD COLUMN configuration_version BIGINT UNSIGNED NOT NULL DEFAULT 1
        AFTER last_error_summary,
    ADD CONSTRAINT chk_ai_connection_configuration_version
        CHECK (configuration_version >= 1),
    ADD CONSTRAINT chk_ai_connection_protocol
        CHECK (protocol IN ('CHAT_COMPLETIONS', 'RESPONSES')),
    ADD CONSTRAINT chk_ai_connection_last_test_status
        CHECK (last_test_status IN ('NOT_TESTED', 'STALE', 'SUCCESS', 'FAILED'));

ALTER TABLE connection_test_record
    ADD COLUMN configuration_version BIGINT UNSIGNED NOT NULL DEFAULT 1
        AFTER capabilities_json,
    ADD CONSTRAINT chk_connection_test_configuration_version
        CHECK (configuration_version >= 1),
    ADD CONSTRAINT chk_connection_test_protocol
        CHECK (protocol IN ('CHAT_COMPLETIONS', 'RESPONSES')),
    ADD CONSTRAINT chk_connection_test_status
        CHECK (status IN ('SUCCESS', 'FAILED'));

ALTER TABLE workspace
    ADD CONSTRAINT chk_workspace_status
        CHECK (status IN ('ACTIVE', 'ARCHIVED'));

ALTER TABLE agent_definition
    ADD CONSTRAINT chk_agent_definition_status
        CHECK (status IN ('ACTIVE', 'DISABLED'));

ALTER TABLE task
    ADD CONSTRAINT chk_task_module_type
        CHECK (module_type IN ('TECH_LEARNING')),
    ADD CONSTRAINT chk_task_expected_artifact_type
        CHECK (expected_artifact_type IN ('LEARNING_NOTE', 'QUIZ', 'SUMMARY')),
    ADD CONSTRAINT chk_task_priority
        CHECK (priority IN ('LOW', 'NORMAL', 'HIGH')),
    ADD CONSTRAINT chk_task_status
        CHECK (status IN (
            'DRAFT', 'READY', 'RUNNING', 'WAITING_USER', 'PAUSED',
            'SUCCEEDED', 'FAILED', 'CANCELLED', 'ARCHIVED'
        ));

ALTER TABLE agent_run
    ADD CONSTRAINT chk_agent_run_status
        CHECK (status IN (
            'QUEUED', 'RUNNING', 'WAITING_USER', 'PAUSING', 'PAUSED',
            'SUCCEEDED', 'FAILED', 'CANCELLING', 'CANCELLED', 'RECOVERY_REQUIRED'
        ));

ALTER TABLE model_call
    ADD CONSTRAINT chk_model_call_protocol
        CHECK (protocol IN ('CHAT_COMPLETIONS', 'RESPONSES')),
    ADD CONSTRAINT chk_model_call_status
        CHECK (status IN ('RUNNING', 'SUCCEEDED', 'PAUSED', 'CANCELLED', 'FAILED'));

ALTER TABLE tool_definition
    ADD CONSTRAINT chk_tool_definition_risk_level
        CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH'));

ALTER TABLE tool_call
    ADD CONSTRAINT chk_tool_call_status
        CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED'));

ALTER TABLE document
    ADD CONSTRAINT chk_document_parse_status
        CHECK (parse_status IN ('READY'));

ALTER TABLE task_document
    ADD CONSTRAINT chk_task_document_relation_type
        CHECK (relation_type IN ('SOURCE'));

ALTER TABLE artifact
    ADD CONSTRAINT chk_artifact_status
        CHECK (status IN ('READY'));

ALTER TABLE artifact_version
    ADD CONSTRAINT chk_artifact_version_content_format
        CHECK (content_format IN ('MARKDOWN'));

CREATE TABLE storage_cleanup_failure (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    resource_type VARCHAR(32) NOT NULL,
    resource_id BIGINT UNSIGNED NULL,
    storage_ref VARCHAR(512) NOT NULL,
    storage_ref_fingerprint CHAR(16) NOT NULL,
    operation VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    failure_type VARCHAR(128) NOT NULL,
    failure_summary VARCHAR(512) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    last_retried_at DATETIME(6) NULL,
    resolved_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_storage_cleanup_status_created (status, created_at, id),
    CONSTRAINT chk_storage_cleanup_resource_type
        CHECK (resource_type IN ('DOCUMENT_UPLOAD')),
    CONSTRAINT chk_storage_cleanup_operation
        CHECK (operation IN ('MOVE_TO_TRASH')),
    CONSTRAINT chk_storage_cleanup_status
        CHECK (status IN ('PENDING', 'RESOLVED')),
    CONSTRAINT chk_storage_cleanup_retry_count
        CHECK (retry_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
