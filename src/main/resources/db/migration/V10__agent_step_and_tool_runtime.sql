CREATE TABLE agent_run_step (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    agent_run_id BIGINT UNSIGNED NOT NULL,
    step_number INT NOT NULL,
    step_type VARCHAR(32) NOT NULL,
    title VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    model_call_id BIGINT UNSIGNED NULL,
    tool_call_id BIGINT UNSIGNED NULL,
    input_summary VARCHAR(512) NULL,
    output_summary VARCHAR(512) NULL,
    started_at DATETIME(6) NULL,
    finished_at DATETIME(6) NULL,
    error_code VARCHAR(64) NULL,
    error_summary VARCHAR(512) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_run_step_number (agent_run_id, step_number),
    UNIQUE KEY uk_agent_run_step_id_run (id, agent_run_id),
    KEY idx_agent_run_step_run_number (agent_run_id, step_number, id),
    CONSTRAINT fk_agent_run_step_run FOREIGN KEY (agent_run_id) REFERENCES agent_run(id),
    CONSTRAINT chk_agent_run_step_number CHECK (step_number >= 1),
    CONSTRAINT chk_agent_run_step_type
        CHECK (step_type IN ('PLAN', 'MODEL', 'TOOL', 'ARTIFACT')),
    CONSTRAINT chk_agent_run_step_status
        CHECK (status IN (
            'PENDING', 'RUNNING', 'WAITING_CONFIRMATION',
            'SUCCEEDED', 'FAILED', 'CANCELLED'
        ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE tool_definition
    DROP INDEX uk_tool_definition_code,
    ADD COLUMN handler_type VARCHAR(32) NOT NULL DEFAULT 'BUILTIN'
        AFTER description,
    ADD COLUMN tool_version INT NOT NULL DEFAULT 1
        AFTER handler_type,
    ADD COLUMN output_schema_json JSON NULL
        AFTER input_schema_json,
    ADD COLUMN timeout_ms INT NOT NULL DEFAULT 30000
        AFTER requires_confirmation,
    ADD COLUMN max_result_bytes INT NOT NULL DEFAULT 262144
        AFTER timeout_ms,
    ADD UNIQUE KEY uk_tool_definition_code_version (tool_code, tool_version),
    ADD CONSTRAINT chk_tool_definition_handler_type
        CHECK (handler_type IN ('BUILTIN')),
    ADD CONSTRAINT chk_tool_definition_version CHECK (tool_version >= 1),
    ADD CONSTRAINT chk_tool_definition_timeout CHECK (timeout_ms BETWEEN 1 AND 120000),
    ADD CONSTRAINT chk_tool_definition_result_size CHECK (max_result_bytes BETWEEN 1 AND 1048576);

UPDATE tool_definition
SET output_schema_json = JSON_OBJECT('type', 'object')
WHERE output_schema_json IS NULL;

ALTER TABLE tool_definition
    MODIFY COLUMN output_schema_json JSON NOT NULL;

ALTER TABLE tool_call
    ADD COLUMN step_id BIGINT UNSIGNED NULL
        AFTER agent_run_id,
    ADD COLUMN tool_code VARCHAR(64) NULL
        AFTER tool_definition_id,
    ADD COLUMN tool_version INT NOT NULL DEFAULT 1
        AFTER tool_code,
    ADD COLUMN arguments_hash CHAR(64) NULL
        AFTER call_key,
    ADD COLUMN arguments_summary VARCHAR(512) NULL
        AFTER arguments_snapshot_ref,
    ADD COLUMN result_summary VARCHAR(512) NULL
        AFTER result_snapshot_ref,
    ADD COLUMN result_size_bytes INT NULL
        AFTER result_summary,
    ADD COLUMN retry_of_tool_call_id BIGINT UNSIGNED NULL
        AFTER result_size_bytes,
    ADD COLUMN updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        AFTER created_at;

UPDATE tool_call tc
JOIN tool_definition td ON td.id = tc.tool_definition_id
SET tc.tool_code = td.tool_code,
    tc.tool_version = td.tool_version,
    tc.arguments_hash = SHA2(COALESCE(tc.arguments_snapshot_ref, tc.call_key), 256);

ALTER TABLE tool_call
    MODIFY COLUMN tool_code VARCHAR(64) NOT NULL,
    MODIFY COLUMN arguments_hash CHAR(64) NOT NULL,
    ADD KEY idx_tool_call_tool_status_created
        (tool_code, status, created_at, id),
    ADD KEY idx_tool_call_step (step_id, id),
    ADD UNIQUE KEY uk_tool_call_id_run (id, agent_run_id),
    ADD CONSTRAINT fk_tool_call_step_run FOREIGN KEY (step_id, agent_run_id)
        REFERENCES agent_run_step(id, agent_run_id),
    ADD CONSTRAINT fk_tool_call_retry FOREIGN KEY (retry_of_tool_call_id)
        REFERENCES tool_call(id),
    ADD CONSTRAINT chk_tool_call_version CHECK (tool_version >= 1),
    ADD CONSTRAINT chk_tool_call_result_size
        CHECK (result_size_bytes IS NULL OR result_size_bytes >= 0);

ALTER TABLE agent_run_step
    ADD CONSTRAINT fk_agent_run_step_model_call FOREIGN KEY (model_call_id, agent_run_id)
        REFERENCES model_call(id, agent_run_id),
    ADD CONSTRAINT fk_agent_run_step_tool_call FOREIGN KEY (tool_call_id, agent_run_id)
        REFERENCES tool_call(id, agent_run_id);

INSERT INTO tool_definition (
    tool_code, name, description, handler_type, tool_version,
    input_schema_json, output_schema_json, risk_level,
    requires_confirmation, timeout_ms, max_result_bytes,
    enabled, created_at, updated_at
) VALUES
(
    'dataset.schema',
    '数据集结构',
    '返回当前分析工作表的字段、类型和基础信息',
    'BUILTIN',
    1,
    JSON_OBJECT(
        'type', 'object',
        'properties', JSON_OBJECT(),
        'additionalProperties', false
    ),
    JSON_OBJECT('type', 'object'),
    'LOW',
    0,
    10000,
    65536,
    1,
    UTC_TIMESTAMP(6),
    UTC_TIMESTAMP(6)
),
(
    'dataset.preview',
    '数据集预览',
    '返回当前分析工作表的有限行预览',
    'BUILTIN',
    1,
    JSON_OBJECT(
        'type', 'object',
        'properties', JSON_OBJECT(
            'offset', JSON_OBJECT('type', 'integer', 'minimum', 0, 'maximum', 99),
            'limit', JSON_OBJECT('type', 'integer', 'minimum', 1, 'maximum', 100)
        ),
        'additionalProperties', false
    ),
    JSON_OBJECT('type', 'object'),
    'LOW',
    0,
    10000,
    262144,
    1,
    UTC_TIMESTAMP(6),
    UTC_TIMESTAMP(6)
),
(
    'dataset.profile',
    '数据集质量摘要',
    '返回当前分析工作表的缺失值、重复值和字段统计',
    'BUILTIN',
    1,
    JSON_OBJECT(
        'type', 'object',
        'properties', JSON_OBJECT(),
        'additionalProperties', false
    ),
    JSON_OBJECT('type', 'object'),
    'LOW',
    0,
    10000,
    262144,
    1,
    UTC_TIMESTAMP(6),
    UTC_TIMESTAMP(6)
),
(
    'dataset.aggregate',
    '数据集聚合',
    '执行白名单计数、去重计数、求和、平均、最小值和最大值',
    'BUILTIN',
    1,
    JSON_OBJECT(
        'type', 'object',
        'required', JSON_ARRAY('operation'),
        'properties', JSON_OBJECT(
            'operation', JSON_OBJECT(
                'type', 'string',
                'enum', JSON_ARRAY(
                    'count', 'distinct_count', 'sum', 'avg', 'min', 'max'
                )
            ),
            'field', JSON_OBJECT('type', 'string', 'maxLength', 255),
            'groupBy', JSON_OBJECT(
                'type', 'array',
                'maxItems', 2,
                'items', JSON_OBJECT('type', 'string', 'maxLength', 255)
            ),
            'sort', JSON_OBJECT(
                'type', 'string',
                'enum', JSON_ARRAY('ASC', 'DESC')
            ),
            'limit', JSON_OBJECT('type', 'integer', 'minimum', 1, 'maximum', 100)
        ),
        'additionalProperties', false
    ),
    JSON_OBJECT('type', 'object'),
    'LOW',
    0,
    30000,
    262144,
    1,
    UTC_TIMESTAMP(6),
    UTC_TIMESTAMP(6)
),
(
    'dataset.chart_spec',
    '数据图表规格',
    '根据受限聚合生成安全的 ECharts JSON 规格',
    'BUILTIN',
    1,
    JSON_OBJECT(
        'type', 'object',
        'required', JSON_ARRAY('chartType', 'categoryField', 'operation'),
        'properties', JSON_OBJECT(
            'chartType', JSON_OBJECT(
                'type', 'string',
                'enum', JSON_ARRAY('bar', 'line', 'pie')
            ),
            'title', JSON_OBJECT('type', 'string', 'maxLength', 120),
            'categoryField', JSON_OBJECT('type', 'string', 'maxLength', 255),
            'valueField', JSON_OBJECT('type', 'string', 'maxLength', 255),
            'operation', JSON_OBJECT(
                'type', 'string',
                'enum', JSON_ARRAY('count', 'distinct_count', 'sum', 'avg', 'min', 'max')
            ),
            'limit', JSON_OBJECT('type', 'integer', 'minimum', 1, 'maximum', 50)
        ),
        'additionalProperties', false
    ),
    JSON_OBJECT('type', 'object'),
    'LOW',
    0,
    30000,
    262144,
    1,
    UTC_TIMESTAMP(6),
    UTC_TIMESTAMP(6)
);
