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
        Runnable controlCheck
) {
    public ToolExecutionContext {
        if (runId == null || stepId == null || workspaceId == null
                || datasetId == null || sheetId == null) {
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
                Map.of(), Map.of(), false, null);
    }

    public ToolExecutionContext(Long runId,
                                Long stepId,
                                Long modelCallId,
                                Long workspaceId,
                                Long datasetId,
                                Long sheetId,
                                Runnable controlCheck) {
        this(runId, stepId, modelCallId, workspaceId, datasetId, sheetId,
                Map.of(), Map.of(), false, controlCheck);
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
                columnOverrides, toolVersionIds, true, controlCheck);
    }

    public void checkControl() {
        if (controlCheck != null) {
            controlCheck.run();
        }
    }
}
