ALTER TABLE task
    DROP CHECK chk_task_module_type,
    DROP CHECK chk_task_expected_artifact_type,
    ADD CONSTRAINT chk_task_module_type
        CHECK (module_type IN (
            'TECH_LEARNING', 'DATA_ANALYSIS', 'CONTENT_CREATION'
        )),
    ADD CONSTRAINT chk_task_expected_artifact_type
        CHECK (expected_artifact_type IN (
            'LEARNING_NOTE', 'QUIZ', 'SUMMARY',
            'ANALYSIS_REPORT', 'CHART_SPEC', 'DATA_EXPORT',
            'CONTENT_DRAFT', 'CONTENT_REVIEW'
        ));

ALTER TABLE artifact
    DROP CHECK chk_artifact_type,
    ADD CONSTRAINT chk_artifact_type
        CHECK (artifact_type IN (
            'LEARNING_NOTE', 'QUIZ', 'SUMMARY',
            'ANALYSIS_REPORT', 'CHART_SPEC', 'DATA_EXPORT',
            'CONTENT_DRAFT', 'CONTENT_REVIEW'
        ));
