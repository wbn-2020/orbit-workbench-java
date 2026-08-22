package com.orbitworkbench.analysis.application;

import java.util.List;
import java.util.Map;

public record DataAnalysisRunContext(
        Long taskId,
        Long workspaceId,
        Long connectionId,
        String taskTitle,
        Long datasetId,
        String datasetName,
        String datasetFormat,
        Long sheetId,
        String sheetName,
        Long rowCount,
        Integer columnCount,
        String analysisGoal,
        List<String> expectedOutputs,
        Map<Long, String> columnOverrides
) {
}
