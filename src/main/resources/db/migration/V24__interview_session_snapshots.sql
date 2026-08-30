ALTER TABLE interview_session
    ADD COLUMN interviewer_name_snapshot VARCHAR(64) NULL AFTER round,
    ADD COLUMN project_bindings_json JSON NULL AFTER interviewer_name_snapshot,
    ADD COLUMN ai_connection_id_snapshot BIGINT UNSIGNED NULL AFTER project_bindings_json,
    ADD COLUMN ai_model_snapshot VARCHAR(128) NULL AFTER ai_connection_id_snapshot,
    ADD COLUMN web_search_policy VARCHAR(16) NULL AFTER ai_model_snapshot,
    ADD CONSTRAINT chk_interview_session_web_search CHECK (
        web_search_policy IS NULL OR web_search_policy IN ('DISABLED', 'ON_DEMAND', 'AUTO')
    );
