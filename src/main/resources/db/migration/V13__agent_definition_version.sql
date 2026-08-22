ALTER TABLE agent_definition
    ADD COLUMN workspace_id BIGINT UNSIGNED NULL AFTER id,
    ADD COLUMN code VARCHAR(128) NULL AFTER name,
    ADD COLUMN published_version_id BIGINT UNSIGNED NULL AFTER prompt_version_id,
    ADD COLUMN version BIGINT UNSIGNED NOT NULL DEFAULT 1 AFTER published_version_id,
    ADD UNIQUE KEY uk_agent_definition_workspace_code (workspace_id, code),
    ADD KEY idx_agent_definition_workspace_status (workspace_id, status, id),
    ADD CONSTRAINT fk_agent_definition_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspace(id),
    ADD CONSTRAINT chk_agent_definition_version
        CHECK (version >= 1);

CREATE TABLE agent_version (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    agent_definition_id BIGINT UNSIGNED NOT NULL,
    version_number INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    connection_id BIGINT UNSIGNED NULL,
    model_profile_id BIGINT UNSIGNED NULL,
    prompt_version_id BIGINT UNSIGNED NULL,
    configuration_json JSON NOT NULL,
    published_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_version_number (agent_definition_id, version_number),
    UNIQUE KEY uk_agent_version_id_definition (id, agent_definition_id),
    KEY idx_agent_version_agent_status (agent_definition_id, status, version_number, id),
    CONSTRAINT fk_agent_version_definition
        FOREIGN KEY (agent_definition_id) REFERENCES agent_definition(id),
    CONSTRAINT fk_agent_version_connection
        FOREIGN KEY (connection_id) REFERENCES ai_connection(id),
    CONSTRAINT fk_agent_version_model
        FOREIGN KEY (model_profile_id) REFERENCES model_profile(id),
    CONSTRAINT fk_agent_version_prompt
        FOREIGN KEY (prompt_version_id) REFERENCES prompt_version(id),
    CONSTRAINT chk_agent_version_status
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'DISABLED')),
    CONSTRAINT chk_agent_version_number
        CHECK (version_number >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE agent_definition
    ADD CONSTRAINT fk_agent_definition_published_version
        FOREIGN KEY (published_version_id) REFERENCES agent_version(id);

ALTER TABLE agent_run
    ADD COLUMN agent_version_id BIGINT UNSIGNED NULL AFTER agent_definition_id,
    ADD KEY idx_agent_run_version_created (agent_version_id, created_at, id),
    ADD CONSTRAINT fk_agent_run_version_definition
        FOREIGN KEY (agent_version_id, agent_definition_id)
            REFERENCES agent_version(id, agent_definition_id);

INSERT INTO agent_version (
    agent_definition_id,
    version_number,
    status,
    connection_id,
    model_profile_id,
    prompt_version_id,
    configuration_json,
    published_at,
    created_at,
    updated_at
)
SELECT ad.id,
       1,
       CASE WHEN ad.status = 'ACTIVE' THEN 'PUBLISHED' ELSE 'DISABLED' END,
       ad.default_connection_id,
       ad.default_model_profile_id,
       ad.prompt_version_id,
       ad.configuration_json,
       CASE WHEN ad.status = 'ACTIVE' THEN UTC_TIMESTAMP(6) ELSE NULL END,
       ad.created_at,
       ad.updated_at
FROM agent_definition ad
WHERE NOT EXISTS (
    SELECT 1
    FROM agent_version av
    WHERE av.agent_definition_id = ad.id
);

UPDATE agent_definition ad
JOIN agent_version av
  ON av.agent_definition_id = ad.id
 AND av.version_number = 1
SET ad.published_version_id = av.id
WHERE ad.published_version_id IS NULL;

UPDATE agent_run ar
JOIN agent_definition ad ON ad.id = ar.agent_definition_id
JOIN agent_version av
  ON av.agent_definition_id = ad.id
 AND av.version_number = 1
SET ar.agent_version_id = av.id
WHERE ar.agent_version_id IS NULL;

ALTER TABLE agent_run
    ADD CONSTRAINT chk_agent_run_version_compatibility
        CHECK (agent_version_id IS NOT NULL OR agent_definition_id IS NOT NULL);
