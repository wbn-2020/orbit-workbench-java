-- P3-M3: Tool/Skill catalog and immutable versioning baseline.
-- Existing tool_definition remains as the ToolCall audit compatibility anchor.

CREATE TABLE tool_catalog (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    tool_code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512) NULL,
    handler_type VARCHAR(32) NOT NULL,
    published_version_id BIGINT UNSIGNED NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    lock_version BIGINT UNSIGNED NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tool_catalog_code (tool_code),
    CONSTRAINT chk_tool_catalog_status
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'DISABLED')),
    CONSTRAINT chk_tool_catalog_lock_version CHECK (lock_version >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tool_version (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    tool_catalog_id BIGINT UNSIGNED NOT NULL,
    version_number INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    input_schema_json JSON NOT NULL,
    output_schema_json JSON NOT NULL,
    risk_level VARCHAR(32) NOT NULL,
    requires_confirmation TINYINT(1) NOT NULL DEFAULT 0,
    timeout_ms INT NOT NULL,
    max_result_bytes INT NOT NULL,
    capabilities_json JSON NOT NULL,
    published_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tool_version_number (tool_catalog_id, version_number),
    UNIQUE KEY uk_tool_version_id_catalog (id, tool_catalog_id),
    KEY idx_tool_version_catalog_status
        (tool_catalog_id, status, version_number, id),
    CONSTRAINT fk_tool_version_catalog
        FOREIGN KEY (tool_catalog_id) REFERENCES tool_catalog(id),
    CONSTRAINT chk_tool_version_status
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'DISABLED')),
    CONSTRAINT chk_tool_version_number CHECK (version_number >= 1),
    CONSTRAINT chk_tool_version_risk
        CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT chk_tool_version_timeout CHECK (timeout_ms > 0),
    CONSTRAINT chk_tool_version_result_size CHECK (max_result_bytes > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE tool_catalog
    ADD CONSTRAINT fk_tool_catalog_published_version
        FOREIGN KEY (published_version_id) REFERENCES tool_version(id);

CREATE TABLE skill_definition (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    workspace_id BIGINT UNSIGNED NOT NULL,
    skill_code VARCHAR(128) NOT NULL,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512) NULL,
    published_version_id BIGINT UNSIGNED NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    lock_version BIGINT UNSIGNED NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_skill_definition_workspace_code (workspace_id, skill_code),
    KEY idx_skill_definition_workspace_status (workspace_id, status, id),
    CONSTRAINT fk_skill_definition_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspace(id),
    CONSTRAINT chk_skill_definition_status
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'DISABLED')),
    CONSTRAINT chk_skill_definition_lock_version CHECK (lock_version >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE skill_version (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    skill_definition_id BIGINT UNSIGNED NOT NULL,
    version_number INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    prompt_version_id BIGINT UNSIGNED NOT NULL,
    input_schema_json JSON NOT NULL,
    output_type VARCHAR(64) NOT NULL,
    runtime_limits_json JSON NOT NULL,
    published_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_skill_version_number (skill_definition_id, version_number),
    UNIQUE KEY uk_skill_version_id_definition (id, skill_definition_id),
    KEY idx_skill_version_definition_status
        (skill_definition_id, status, version_number, id),
    CONSTRAINT fk_skill_version_definition
        FOREIGN KEY (skill_definition_id) REFERENCES skill_definition(id),
    CONSTRAINT fk_skill_version_prompt
        FOREIGN KEY (prompt_version_id) REFERENCES prompt_version(id),
    CONSTRAINT chk_skill_version_status
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'DISABLED')),
    CONSTRAINT chk_skill_version_number CHECK (version_number >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE skill_definition
    ADD CONSTRAINT fk_skill_definition_published_version
        FOREIGN KEY (published_version_id) REFERENCES skill_version(id);

CREATE TABLE skill_version_tool (
    skill_version_id BIGINT UNSIGNED NOT NULL,
    tool_version_id BIGINT UNSIGNED NOT NULL,
    binding_order INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (skill_version_id, tool_version_id),
    UNIQUE KEY uk_skill_version_tool_order (skill_version_id, binding_order),
    CONSTRAINT fk_skill_version_tool_skill
        FOREIGN KEY (skill_version_id) REFERENCES skill_version(id),
    CONSTRAINT fk_skill_version_tool_tool
        FOREIGN KEY (tool_version_id) REFERENCES tool_version(id),
    CONSTRAINT chk_skill_version_tool_order CHECK (binding_order >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO tool_catalog (
    tool_code, name, description, handler_type, status,
    lock_version, created_at, updated_at
)
SELECT td.tool_code, td.name, td.description, td.handler_type,
       CASE WHEN EXISTS (
           SELECT 1
           FROM tool_definition enabled_td
           WHERE enabled_td.tool_code = td.tool_code
             AND enabled_td.enabled = 1
       ) THEN 'PUBLISHED' ELSE 'DISABLED' END,
       1, td.created_at, td.updated_at
FROM tool_definition td
JOIN (
    SELECT tool_code,
           COALESCE(
               MAX(CASE WHEN enabled = 1 THEN tool_version END),
               MAX(tool_version)
           ) AS selected_tool_version
    FROM tool_definition
    GROUP BY tool_code
) selected
  ON selected.tool_code = td.tool_code
 AND selected.selected_tool_version = td.tool_version;

INSERT INTO tool_version (
    tool_catalog_id, version_number, status, input_schema_json,
    output_schema_json, risk_level, requires_confirmation, timeout_ms,
    max_result_bytes, capabilities_json, published_at, created_at, updated_at
)
SELECT tc.id, td.tool_version,
       CASE WHEN published.tool_version = td.tool_version
            THEN 'PUBLISHED' ELSE 'DISABLED' END,
       td.input_schema_json, td.output_schema_json, td.risk_level,
       td.requires_confirmation, td.timeout_ms, td.max_result_bytes,
       JSON_OBJECT('handlerType', td.handler_type),
       CASE WHEN published.tool_version = td.tool_version
            THEN td.updated_at ELSE NULL END,
       td.created_at, td.updated_at
FROM tool_definition td
JOIN tool_catalog tc ON tc.tool_code = td.tool_code
LEFT JOIN (
    SELECT tool_code, MAX(tool_version) AS tool_version
    FROM tool_definition
    WHERE enabled = 1
    GROUP BY tool_code
) published
  ON published.tool_code = td.tool_code;

UPDATE tool_catalog tc
JOIN tool_version tv
  ON tv.tool_catalog_id = tc.id
 AND tv.status = 'PUBLISHED'
SET tc.published_version_id = tv.id;

ALTER TABLE tool_call
    MODIFY COLUMN tool_definition_id BIGINT UNSIGNED NULL,
    ADD COLUMN tool_version_id BIGINT UNSIGNED NULL AFTER tool_definition_id,
    ADD KEY idx_tool_call_version_created (tool_version_id, created_at, id),
    ADD CONSTRAINT fk_tool_call_version
        FOREIGN KEY (tool_version_id) REFERENCES tool_version(id);

UPDATE tool_call call_record
JOIN tool_catalog catalog ON catalog.tool_code = call_record.tool_code
JOIN tool_version version_record
  ON version_record.tool_catalog_id = catalog.id
 AND version_record.version_number = call_record.tool_version
SET call_record.tool_version_id = version_record.id
WHERE call_record.tool_version_id IS NULL;
