-- P3-M4: controlled MCP Server configuration and synchronized Tool metadata.

CREATE TABLE mcp_server (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    workspace_id BIGINT UNSIGNED NOT NULL,
    server_code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    transport VARCHAR(32) NOT NULL,
    endpoint_url VARCHAR(1024) NOT NULL,
    credential_ref VARCHAR(256) NULL,
    allow_private_network TINYINT(1) NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'DISABLED',
    sync_status VARCHAR(32) NOT NULL DEFAULT 'NEVER',
    last_sync_at DATETIME(6) NULL,
    last_error_code VARCHAR(64) NULL,
    last_error_summary VARCHAR(512) NULL,
    lock_version BIGINT UNSIGNED NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mcp_server_workspace_code (workspace_id, server_code),
    KEY idx_mcp_server_workspace_status (workspace_id, status, id),
    CONSTRAINT fk_mcp_server_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspace(id),
    CONSTRAINT chk_mcp_server_transport
        CHECK (transport IN ('STREAMABLE_HTTP')),
    CONSTRAINT chk_mcp_server_status
        CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT chk_mcp_server_sync_status
        CHECK (sync_status IN ('NEVER', 'SUCCESS', 'FAILED')),
    CONSTRAINT chk_mcp_server_lock_version CHECK (lock_version >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE mcp_tool (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    mcp_server_id BIGINT UNSIGNED NOT NULL,
    tool_code VARCHAR(64) NOT NULL,
    tool_name VARCHAR(256) NOT NULL,
    title VARCHAR(256) NULL,
    description VARCHAR(1024) NULL,
    tool_catalog_id BIGINT UNSIGNED NOT NULL,
    tool_version_id BIGINT UNSIGNED NOT NULL,
    version_number INT NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mcp_tool_server_name (mcp_server_id, tool_name),
    UNIQUE KEY uk_mcp_tool_code (tool_code),
    KEY idx_mcp_tool_server_enabled (mcp_server_id, enabled, id),
    CONSTRAINT fk_mcp_tool_server
        FOREIGN KEY (mcp_server_id) REFERENCES mcp_server(id),
    CONSTRAINT fk_mcp_tool_catalog
        FOREIGN KEY (tool_catalog_id) REFERENCES tool_catalog(id),
    CONSTRAINT fk_mcp_tool_version
        FOREIGN KEY (tool_version_id) REFERENCES tool_version(id),
    CONSTRAINT chk_mcp_tool_version_number CHECK (version_number >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
