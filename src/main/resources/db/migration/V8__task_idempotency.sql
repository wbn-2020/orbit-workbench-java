CREATE TABLE task_create_idempotency (
    idempotency_key VARCHAR(128) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    task_id BIGINT UNSIGNED NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (idempotency_key),
    UNIQUE KEY uk_task_create_idempotency_task (task_id),
    CONSTRAINT fk_task_create_idempotency_task
        FOREIGN KEY (task_id) REFERENCES task(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
