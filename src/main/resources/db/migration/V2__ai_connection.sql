CREATE TABLE provider_catalog (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    provider_code VARCHAR(64) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    adapter_type VARCHAR(64) NOT NULL,
    default_protocols JSON NOT NULL,
    capabilities_json JSON NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_provider_catalog_code (provider_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ai_connection (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    provider_catalog_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(128) NOT NULL,
    base_url VARCHAR(512) NOT NULL,
    endpoint_path VARCHAR(255) NOT NULL,
    protocol VARCHAR(32) NOT NULL,
    credential_ciphertext VARBINARY(2048) NULL,
    credential_iv VARBINARY(32) NULL,
    credential_key_version INT NULL,
    credential_masked VARCHAR(32) NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    timeout_ms INT NOT NULL DEFAULT 30000,
    last_test_status VARCHAR(32) NOT NULL DEFAULT 'NOT_TESTED',
    last_tested_at DATETIME(6) NULL,
    last_test_latency_ms INT NULL,
    last_error_code VARCHAR(64) NULL,
    last_error_summary VARCHAR(512) NULL,
    deleted_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ai_connection_provider FOREIGN KEY (provider_catalog_id) REFERENCES provider_catalog(id),
    KEY idx_ai_connection_enabled_updated (enabled, updated_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE model_profile (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    connection_id BIGINT UNSIGNED NOT NULL,
    model_name VARCHAR(128) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    supported_protocols JSON NOT NULL,
    capabilities_json JSON NOT NULL,
    default_parameters_json JSON NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_model_profile_connection FOREIGN KEY (connection_id) REFERENCES ai_connection(id),
    UNIQUE KEY uk_model_profile_connection (connection_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE connection_test_record (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    connection_id BIGINT UNSIGNED NOT NULL,
    protocol VARCHAR(32) NOT NULL,
    model_name VARCHAR(128) NOT NULL,
    streaming TINYINT(1) NOT NULL,
    status VARCHAR(32) NOT NULL,
    http_status INT NULL,
    latency_ms INT NULL,
    event_count INT NOT NULL DEFAULT 0,
    done_marker_received TINYINT(1) NOT NULL DEFAULT 0,
    error_code VARCHAR(64) NULL,
    error_summary VARCHAR(512) NULL,
    capabilities_json JSON NULL,
    tested_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_connection_test_connection FOREIGN KEY (connection_id) REFERENCES ai_connection(id),
    KEY idx_connection_test_connection_time (connection_id, tested_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
