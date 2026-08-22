CREATE TABLE workflow_definition (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    workspace_id BIGINT UNSIGNED NOT NULL,
    code VARCHAR(128) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    published_version_id BIGINT UNSIGNED NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_workflow_definition_workspace_code (workspace_id, code),
    UNIQUE KEY uk_workflow_definition_id_workspace (id, workspace_id),
    KEY idx_workflow_definition_workspace_status (workspace_id, status, updated_at, id),
    CONSTRAINT fk_workflow_definition_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspace(id),
    CONSTRAINT chk_workflow_definition_status
        CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT chk_workflow_definition_version
        CHECK (version >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE workflow_version (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    workflow_definition_id BIGINT UNSIGNED NOT NULL,
    version_number INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    metadata_json JSON NOT NULL,
    published_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_workflow_version_number (workflow_definition_id, version_number),
    UNIQUE KEY uk_workflow_version_id_definition (id, workflow_definition_id),
    KEY idx_workflow_version_definition_status (workflow_definition_id, status, version_number, id),
    CONSTRAINT fk_workflow_version_definition
        FOREIGN KEY (workflow_definition_id) REFERENCES workflow_definition(id),
    CONSTRAINT chk_workflow_version_status
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'DISABLED')),
    CONSTRAINT chk_workflow_version_number
        CHECK (version_number >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE workflow_definition
    ADD CONSTRAINT fk_workflow_definition_published_version
        FOREIGN KEY (published_version_id, id)
            REFERENCES workflow_version(id, workflow_definition_id);

CREATE TABLE workflow_node (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    workflow_version_id BIGINT UNSIGNED NOT NULL,
    node_key VARCHAR(64) NOT NULL,
    node_type VARCHAR(16) NOT NULL,
    name VARCHAR(128) NOT NULL,
    config_json JSON NOT NULL,
    position_json JSON NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_workflow_node_version_key (workflow_version_id, node_key),
    UNIQUE KEY uk_workflow_node_id_version (id, workflow_version_id),
    KEY idx_workflow_node_version_type (workflow_version_id, node_type, id),
    CONSTRAINT fk_workflow_node_version
        FOREIGN KEY (workflow_version_id) REFERENCES workflow_version(id),
    CONSTRAINT chk_workflow_node_type
        CHECK (node_type IN ('START', 'AGENT', 'TOOL', 'APPROVAL', 'CONDITION', 'END'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE workflow_edge (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    workflow_version_id BIGINT UNSIGNED NOT NULL,
    source_node_id BIGINT UNSIGNED NOT NULL,
    target_node_id BIGINT UNSIGNED NOT NULL,
    branch_key VARCHAR(32) NOT NULL DEFAULT 'DEFAULT',
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_workflow_edge_version_route
        (workflow_version_id, source_node_id, target_node_id, branch_key),
    KEY idx_workflow_edge_source (workflow_version_id, source_node_id, sort_order, id),
    KEY idx_workflow_edge_target (workflow_version_id, target_node_id, id),
    CONSTRAINT fk_workflow_edge_version
        FOREIGN KEY (workflow_version_id) REFERENCES workflow_version(id),
    CONSTRAINT fk_workflow_edge_source
        FOREIGN KEY (source_node_id, workflow_version_id)
            REFERENCES workflow_node(id, workflow_version_id),
    CONSTRAINT fk_workflow_edge_target
        FOREIGN KEY (target_node_id, workflow_version_id)
            REFERENCES workflow_node(id, workflow_version_id),
    CONSTRAINT chk_workflow_edge_branch
        CHECK (branch_key IN ('DEFAULT', 'TRUE', 'FALSE')),
    CONSTRAINT chk_workflow_edge_sort_order
        CHECK (sort_order >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE workflow_run (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    workspace_id BIGINT UNSIGNED NOT NULL,
    workflow_definition_id BIGINT UNSIGNED NOT NULL,
    workflow_version_id BIGINT UNSIGNED NOT NULL,
    status VARCHAR(32) NOT NULL,
    input_json JSON NOT NULL,
    output_json JSON NULL,
    current_node_key VARCHAR(64) NULL,
    started_at DATETIME(6) NULL,
    finished_at DATETIME(6) NULL,
    error_code VARCHAR(64) NULL,
    error_summary VARCHAR(512) NULL,
    event_sequence BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_workflow_run_id_workspace (id, workspace_id),
    KEY idx_workflow_run_definition_created
        (workflow_definition_id, created_at, id),
    KEY idx_workflow_run_status_created
        (status, created_at, id),
    CONSTRAINT fk_workflow_run_workspace_definition
        FOREIGN KEY (workflow_definition_id, workspace_id)
            REFERENCES workflow_definition(id, workspace_id),
    CONSTRAINT fk_workflow_run_version_definition
        FOREIGN KEY (workflow_version_id, workflow_definition_id)
            REFERENCES workflow_version(id, workflow_definition_id),
    CONSTRAINT chk_workflow_run_status
        CHECK (status IN (
            'QUEUED', 'RUNNING', 'WAITING_APPROVAL',
            'SUCCEEDED', 'FAILED', 'CANCELLED'
        ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE workflow_node_run (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    workflow_run_id BIGINT UNSIGNED NOT NULL,
    workflow_version_id BIGINT UNSIGNED NOT NULL,
    workflow_node_id BIGINT UNSIGNED NOT NULL,
    node_key VARCHAR(64) NOT NULL,
    node_type VARCHAR(16) NOT NULL,
    sequence INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    input_summary VARCHAR(512) NULL,
    output_summary VARCHAR(512) NULL,
    agent_run_id BIGINT UNSIGNED NULL,
    agent_run_step_id BIGINT UNSIGNED NULL,
    tool_call_id BIGINT UNSIGNED NULL,
    started_at DATETIME(6) NULL,
    finished_at DATETIME(6) NULL,
    error_code VARCHAR(64) NULL,
    error_summary VARCHAR(512) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_workflow_node_run_sequence (workflow_run_id, sequence),
    UNIQUE KEY uk_workflow_node_run_id_version (id, workflow_version_id),
    KEY idx_workflow_node_run_run_sequence (workflow_run_id, sequence, id),
    CONSTRAINT fk_workflow_node_run_run
        FOREIGN KEY (workflow_run_id) REFERENCES workflow_run(id),
    CONSTRAINT fk_workflow_node_run_node
        FOREIGN KEY (workflow_node_id, workflow_version_id)
            REFERENCES workflow_node(id, workflow_version_id),
    CONSTRAINT fk_workflow_node_run_agent_run
        FOREIGN KEY (agent_run_id) REFERENCES agent_run(id),
    CONSTRAINT fk_workflow_node_run_agent_step
        FOREIGN KEY (agent_run_step_id) REFERENCES agent_run_step(id),
    CONSTRAINT fk_workflow_node_run_tool_call
        FOREIGN KEY (tool_call_id) REFERENCES tool_call(id),
    CONSTRAINT chk_workflow_node_run_status
        CHECK (status IN (
            'PENDING', 'RUNNING', 'WAITING_APPROVAL',
            'SUCCEEDED', 'FAILED', 'CANCELLED', 'SKIPPED'
        )),
    CONSTRAINT chk_workflow_node_run_sequence
        CHECK (sequence >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE workflow_run_event (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    workflow_run_id BIGINT UNSIGNED NOT NULL,
    sequence BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    event_summary VARCHAR(512) NULL,
    payload_json JSON NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_workflow_run_event_sequence (workflow_run_id, sequence),
    CONSTRAINT fk_workflow_run_event_run
        FOREIGN KEY (workflow_run_id) REFERENCES workflow_run(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
