CREATE TABLE workspace (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512) NULL,
    default_key TINYINT UNSIGNED NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_workspace_default (default_key),
    CONSTRAINT chk_workspace_default_key
        CHECK (default_key IS NULL OR default_key = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE prompt_template (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL,
    purpose VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE prompt_version (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    template_id BIGINT UNSIGNED NOT NULL,
    version_number INT NOT NULL,
    content MEDIUMTEXT NOT NULL,
    variables_json JSON NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_prompt_version_template FOREIGN KEY (template_id) REFERENCES prompt_template(id),
    UNIQUE KEY uk_prompt_version_number (template_id, version_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE agent_definition (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512) NULL,
    status VARCHAR(32) NOT NULL,
    default_connection_id BIGINT UNSIGNED NULL,
    default_model_profile_id BIGINT UNSIGNED NULL,
    prompt_version_id BIGINT UNSIGNED NULL,
    configuration_json JSON NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_agent_definition_connection FOREIGN KEY (default_connection_id) REFERENCES ai_connection(id),
    CONSTRAINT fk_agent_definition_model FOREIGN KEY (default_model_profile_id) REFERENCES model_profile(id),
    CONSTRAINT fk_agent_definition_prompt FOREIGN KEY (prompt_version_id) REFERENCES prompt_version(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE task (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    workspace_id BIGINT UNSIGNED NOT NULL,
    connection_id BIGINT UNSIGNED NOT NULL,
    module_type VARCHAR(32) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description MEDIUMTEXT NULL,
    expected_artifact_type VARCHAR(64) NOT NULL,
    priority VARCHAR(16) NOT NULL,
    status VARCHAR(32) NOT NULL,
    current_run_id BIGINT UNSIGNED NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_id_workspace (id, workspace_id),
    CONSTRAINT fk_task_workspace FOREIGN KEY (workspace_id) REFERENCES workspace(id),
    CONSTRAINT fk_task_connection FOREIGN KEY (connection_id) REFERENCES ai_connection(id),
    KEY idx_task_workspace_status_updated (workspace_id, status, updated_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE agent_run (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    task_id BIGINT UNSIGNED NOT NULL,
    agent_definition_id BIGINT UNSIGNED NOT NULL,
    connection_id BIGINT UNSIGNED NOT NULL,
    status VARCHAR(32) NOT NULL,
    current_step VARCHAR(128) NULL,
    started_at DATETIME(6) NULL,
    finished_at DATETIME(6) NULL,
    last_heartbeat_at DATETIME(6) NULL,
    cancel_requested_at DATETIME(6) NULL,
    retry_of_run_id BIGINT UNSIGNED NULL,
    error_code VARCHAR(64) NULL,
    error_summary VARCHAR(512) NULL,
    trace_id VARCHAR(64) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_run_id_task (id, task_id),
    CONSTRAINT fk_agent_run_task FOREIGN KEY (task_id) REFERENCES task(id),
    CONSTRAINT fk_agent_run_definition FOREIGN KEY (agent_definition_id) REFERENCES agent_definition(id),
    CONSTRAINT fk_agent_run_connection FOREIGN KEY (connection_id) REFERENCES ai_connection(id),
    CONSTRAINT fk_agent_run_retry_task FOREIGN KEY (retry_of_run_id, task_id)
        REFERENCES agent_run(id, task_id),
    KEY idx_agent_run_task_created (task_id, created_at, id),
    KEY idx_agent_run_status_heartbeat (status, last_heartbeat_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE task
    ADD CONSTRAINT fk_task_current_run FOREIGN KEY (current_run_id, id)
        REFERENCES agent_run(id, task_id);

CREATE TABLE model_call (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    agent_run_id BIGINT UNSIGNED NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    connection_id BIGINT UNSIGNED NOT NULL,
    model_profile_id BIGINT UNSIGNED NULL,
    connection_name_snapshot VARCHAR(128) NULL,
    model_name_snapshot VARCHAR(128) NULL,
    protocol VARCHAR(32) NOT NULL,
    streaming TINYINT(1) NOT NULL,
    status VARCHAR(32) NOT NULL,
    started_at DATETIME(6) NULL,
    finished_at DATETIME(6) NULL,
    input_token_count INT NULL,
    output_token_count INT NULL,
    cached_token_count INT NULL,
    cost_amount DECIMAL(18, 8) NULL,
    cost_currency VARCHAR(8) NULL,
    provider_request_id VARCHAR(128) NULL,
    previous_response_id VARCHAR(128) NULL,
    error_code VARCHAR(64) NULL,
    error_summary VARCHAR(512) NULL,
    retry_count INT NOT NULL DEFAULT 0,
    request_snapshot_ref VARCHAR(512) NULL,
    response_snapshot_ref VARCHAR(512) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_model_call_request_id (request_id),
    UNIQUE KEY uk_model_call_id_run (id, agent_run_id),
    CONSTRAINT fk_model_call_run FOREIGN KEY (agent_run_id) REFERENCES agent_run(id),
    CONSTRAINT fk_model_call_connection FOREIGN KEY (connection_id) REFERENCES ai_connection(id),
    CONSTRAINT fk_model_call_profile FOREIGN KEY (model_profile_id) REFERENCES model_profile(id),
    KEY idx_model_call_run_created (agent_run_id, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE run_event (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    agent_run_id BIGINT UNSIGNED NOT NULL,
    model_call_id BIGINT UNSIGNED NULL,
    sequence BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    event_summary VARCHAR(512) NULL,
    payload_json JSON NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_run_event_run FOREIGN KEY (agent_run_id) REFERENCES agent_run(id),
    CONSTRAINT fk_run_event_model_call_run FOREIGN KEY (model_call_id, agent_run_id)
        REFERENCES model_call(id, agent_run_id),
    UNIQUE KEY uk_run_event_run_sequence (agent_run_id, sequence)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tool_definition (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    tool_code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512) NULL,
    input_schema_json JSON NOT NULL,
    risk_level VARCHAR(32) NOT NULL,
    requires_confirmation TINYINT(1) NOT NULL DEFAULT 0,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tool_definition_code (tool_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tool_call (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    agent_run_id BIGINT UNSIGNED NOT NULL,
    model_call_id BIGINT UNSIGNED NULL,
    tool_definition_id BIGINT UNSIGNED NOT NULL,
    call_key VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    arguments_snapshot_ref VARCHAR(512) NULL,
    result_snapshot_ref VARCHAR(512) NULL,
    started_at DATETIME(6) NULL,
    finished_at DATETIME(6) NULL,
    error_code VARCHAR(64) NULL,
    error_summary VARCHAR(512) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_tool_call_run FOREIGN KEY (agent_run_id) REFERENCES agent_run(id),
    CONSTRAINT fk_tool_call_model_run FOREIGN KEY (model_call_id, agent_run_id)
        REFERENCES model_call(id, agent_run_id),
    CONSTRAINT fk_tool_call_definition FOREIGN KEY (tool_definition_id) REFERENCES tool_definition(id),
    UNIQUE KEY uk_tool_call_call_key (call_key),
    KEY idx_tool_call_run_created (agent_run_id, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
