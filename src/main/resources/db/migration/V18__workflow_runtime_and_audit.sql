-- P3-M4: enable the constrained Workflow -> Tool runtime path.
-- Existing Agent ToolCall rows remain scoped by agent_run_id.

ALTER TABLE workflow_node_run
    ADD UNIQUE KEY uk_workflow_node_run_id_run (id, workflow_run_id);

ALTER TABLE tool_call
    MODIFY COLUMN agent_run_id BIGINT UNSIGNED NULL,
    ADD COLUMN workflow_run_id BIGINT UNSIGNED NULL
        AFTER agent_run_id,
    ADD COLUMN workflow_node_run_id BIGINT UNSIGNED NULL
        AFTER workflow_run_id,
    ADD KEY idx_tool_call_workflow_created
        (workflow_run_id, created_at, id),
    ADD KEY idx_tool_call_workflow_node
        (workflow_node_run_id, workflow_run_id, id),
    ADD CONSTRAINT fk_tool_call_workflow_run
        FOREIGN KEY (workflow_run_id) REFERENCES workflow_run(id),
    ADD CONSTRAINT fk_tool_call_workflow_node_run
        FOREIGN KEY (workflow_node_run_id, workflow_run_id)
            REFERENCES workflow_node_run(id, workflow_run_id),
    ADD CONSTRAINT chk_tool_call_execution_scope
        CHECK (
            (agent_run_id IS NOT NULL AND workflow_run_id IS NULL)
            OR
            (agent_run_id IS NULL AND workflow_run_id IS NOT NULL)
        ),
    ADD CONSTRAINT chk_tool_call_workflow_node_scope
        CHECK (
            (workflow_run_id IS NULL AND workflow_node_run_id IS NULL)
            OR
            (workflow_run_id IS NOT NULL AND workflow_node_run_id IS NOT NULL)
        );
