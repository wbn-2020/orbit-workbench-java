package com.orbitworkbench.tool.application;

import java.util.Map;

public record ToolExecutionContext(
        Long runId,
        Long stepId,
        Long modelCallId,
        Long workspaceId,
        Long datasetId,
        Long sheetId,
        Map<Long, String> columnOverrides,
        Map<String, Long> toolVersionIds,
        boolean enforceToolScope,
        Runnable controlCheck,
        Long workflowRunId,
        Long workflowNodeRunId
) {
    public ToolExecutionContext {
        if ((runId == null) == (workflowRunId == null)
                || workspaceId == null
                || (runId != null && (stepId == null
                || datasetId == null
                || sheetId == null))
                || (workflowRunId != null && workflowNodeRunId == null)) {
            throw new IllegalArgumentException("工具执行上下文不完整");
        }
        columnOverrides = columnOverrides == null
                ? Map.of()
                : Map.copyOf(columnOverrides);
        toolVersionIds = toolVersionIds == null
                ? Map.of()
                : Map.copyOf(toolVersionIds);
    }

    public ToolExecutionContext(Long runId,
                                Long stepId,
                                Long modelCallId,
                                Long workspaceId,
                                Long datasetId,
                                Long sheetId) {
        this(runId, stepId, modelCallId, workspaceId, datasetId, sheetId,
                Map.of(), Map.of(), false, null, null, null);
    }

    public ToolExecutionContext(Long runId,
                                Long stepId,
                                Long modelCallId,
                                Long workspaceId,
                                Long datasetId,
                                Long sheetId,
                                Runnable controlCheck) {
        this(runId, stepId, modelCallId, workspaceId, datasetId, sheetId,
                Map.of(), Map.of(), false, controlCheck, null, null);
    }

    public ToolExecutionContext(Long runId,
                                Long stepId,
                                Long modelCallId,
                                Long workspaceId,
                                Long datasetId,
                                Long sheetId,
                                Map<Long, String> columnOverrides,
                                Map<String, Long> toolVersionIds,
                                Runnable controlCheck) {
        this(runId, stepId, modelCallId, workspaceId, datasetId, sheetId,
                columnOverrides, toolVersionIds, true, controlCheck, null, null);
    }

    public ToolExecutionContext(Long runId,
                                Long stepId,
                                Long modelCallId,
                                Long workspaceId,
                                Long datasetId,
                                Long sheetId,
                                Map<Long, String> columnOverrides,
                                Map<String, Long> toolVersionIds,
                                boolean enforceToolScope,
                                Runnable controlCheck) {
        this(runId, stepId, modelCallId, workspaceId, datasetId, sheetId,
                columnOverrides, toolVersionIds, enforceToolScope,
                controlCheck, null, null);
    }

    public static ToolExecutionContext forWorkflow(
            Long workflowRunId,
            Long workflowNodeRunId,
            Long workspaceId,
            Map<String, Long> toolVersionIds,
            Runnable controlCheck) {
        return new ToolExecutionContext(
                null,
                null,
                null,
                workspaceId,
                null,
                null,
                Map.of(),
                toolVersionIds,
                true,
                controlCheck,
                workflowRunId,
                workflowNodeRunId);
    }

    public boolean workflowScope() {
        return workflowRunId != null;
    }

    public String auditScopeKey() {
        return workflowScope()
                ? "workflow:" + workflowRunId
                : "agent:" + runId;
    }

    public void checkControl() {
        if (controlCheck != null) {
            controlCheck.run();
        }
    }
}
