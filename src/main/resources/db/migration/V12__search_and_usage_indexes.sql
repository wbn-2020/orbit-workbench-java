CREATE INDEX idx_dataset_workspace_hash_deleted
    ON dataset (workspace_id, content_hash, deleted_at);

CREATE INDEX idx_task_workspace_id
    ON task (workspace_id, id);

CREATE INDEX idx_dataset_workspace_id
    ON dataset (workspace_id, id);

CREATE INDEX idx_document_workspace_id
    ON document (workspace_id, id);

CREATE INDEX idx_artifact_workspace_id
    ON artifact (workspace_id, id);

CREATE INDEX idx_data_analysis_task_dataset_sheet_task
    ON data_analysis_task (dataset_id, sheet_id, task_id);

CREATE INDEX idx_model_call_connection_status_created
    ON model_call (connection_id, status, created_at, id);

CREATE INDEX idx_artifact_export_artifact_created
    ON artifact_export (artifact_id, created_at, id);

CREATE INDEX idx_artifact_export_status_created
    ON artifact_export (status, created_at, id);
