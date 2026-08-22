ALTER TABLE task
    DROP CHECK chk_task_module_type,
    DROP CHECK chk_task_expected_artifact_type,
    ADD CONSTRAINT chk_task_module_type
        CHECK (module_type IN ('TECH_LEARNING', 'DATA_ANALYSIS')),
    ADD CONSTRAINT chk_task_expected_artifact_type
        CHECK (expected_artifact_type IN (
            'LEARNING_NOTE', 'QUIZ', 'SUMMARY',
            'ANALYSIS_REPORT', 'CHART_SPEC', 'DATA_EXPORT'
        ));

ALTER TABLE artifact
    ADD CONSTRAINT chk_artifact_type
        CHECK (artifact_type IN (
            'LEARNING_NOTE', 'QUIZ', 'SUMMARY',
            'ANALYSIS_REPORT', 'CHART_SPEC', 'DATA_EXPORT'
        ));

ALTER TABLE artifact_version
    DROP CHECK chk_artifact_version_content_format,
    ADD CONSTRAINT chk_artifact_version_content_format
        CHECK (content_format IN ('MARKDOWN', 'JSON', 'CSV'));

CREATE TABLE data_analysis_task (
    task_id BIGINT UNSIGNED NOT NULL,
    dataset_id BIGINT UNSIGNED NOT NULL,
    sheet_id BIGINT UNSIGNED NOT NULL,
    analysis_goal MEDIUMTEXT NOT NULL,
    expected_outputs_json JSON NOT NULL,
    column_overrides_json JSON NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (task_id),
    CONSTRAINT fk_data_analysis_task_task
        FOREIGN KEY (task_id) REFERENCES task(id),
    CONSTRAINT fk_data_analysis_task_dataset
        FOREIGN KEY (dataset_id) REFERENCES dataset(id),
    CONSTRAINT fk_data_analysis_task_sheet_dataset
        FOREIGN KEY (sheet_id, dataset_id) REFERENCES dataset_sheet(id, dataset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE artifact_export (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    artifact_id BIGINT UNSIGNED NOT NULL,
    artifact_version_id BIGINT UNSIGNED NOT NULL,
    export_key VARCHAR(128) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    format VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    storage_ref VARCHAR(512) NULL,
    size_bytes BIGINT NULL,
    content_hash CHAR(64) NULL,
    error_code VARCHAR(64) NULL,
    error_summary VARCHAR(512) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_artifact_export_key (export_key),
    UNIQUE KEY uk_artifact_export_id_artifact (id, artifact_id),
    CONSTRAINT fk_artifact_export_artifact
        FOREIGN KEY (artifact_id) REFERENCES artifact(id),
    CONSTRAINT fk_artifact_export_version_artifact
        FOREIGN KEY (artifact_version_id, artifact_id)
        REFERENCES artifact_version(id, artifact_id),
    CONSTRAINT chk_artifact_export_format
        CHECK (format IN ('MARKDOWN', 'JSON', 'CSV')),
    CONSTRAINT chk_artifact_export_status
        CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT chk_artifact_export_size
        CHECK (size_bytes IS NULL OR size_bytes >= 0),
    CONSTRAINT chk_artifact_export_success_payload
        CHECK (
            status != 'SUCCEEDED'
            OR (
                storage_ref IS NOT NULL
                AND size_bytes IS NOT NULL
                AND content_hash IS NOT NULL
                AND finished_at IS NOT NULL
            )
        )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO prompt_template (name, purpose, created_at, updated_at)
VALUES (
    '数据分析任务',
    '基于单个数据集工作表执行受限分析并生成可追踪报告和图表',
    UTC_TIMESTAMP(6),
    UTC_TIMESTAMP(6)
);

INSERT INTO prompt_version (
    template_id, version_number, content, variables_json, created_at
)
SELECT id,
       1,
       '你是一名数据分析助手。围绕用户目标制定有限步骤，只能调用已提供的只读数据工具，不得生成或执行 SQL、脚本或文件路径。结论必须引用工具结果；最终输出 Markdown 分析报告，包含数据集、工作表、行数、分析步骤、关键结论、限制和生成时间。需要图表时仅生成受限 JSON 图表规格。',
       JSON_ARRAY(
           'taskTitle', 'analysisGoal', 'datasetName', 'sheetName',
           'datasetSchema', 'datasetProfile', 'toolResults'
       ),
       UTC_TIMESTAMP(6)
FROM prompt_template
WHERE name = '数据分析任务';

INSERT INTO agent_definition (
    name, description, status, default_connection_id, default_model_profile_id,
    prompt_version_id, configuration_json, created_at, updated_at
)
SELECT '数据分析 Agent',
       '使用只读数据工具完成单数据集单工作表分析',
       'ACTIVE',
       NULL,
       NULL,
       pv.id,
       JSON_OBJECT(
           'moduleType', 'DATA_ANALYSIS',
           'maxSteps', 8,
           'maxToolCalls', 20,
           'maxContextBytes', 1048576
       ),
       UTC_TIMESTAMP(6),
       UTC_TIMESTAMP(6)
FROM prompt_version pv
JOIN prompt_template pt ON pt.id = pv.template_id
WHERE pt.name = '数据分析任务'
  AND pv.version_number = 1;
