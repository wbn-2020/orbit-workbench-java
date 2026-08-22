package com.orbitworkbench.analysis.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class DataAnalysisDtos {

    private DataAnalysisDtos() {
    }

    public record CreateDataAnalysisTaskRequest(
            @NotNull @Positive Long workspaceId,
            @NotNull @Positive Long connectionId,
            @NotNull @Positive Long datasetId,
            @NotNull @Positive Long sheetId,
            @NotBlank @Size(max = 120) String title,
            @NotBlank @Size(max = 2000) String analysisGoal,
            @NotNull @Size(min = 1, max = 3) List<@NotBlank String> expectedOutputs,
            @NotBlank String priority,
            @Size(max = 200) Map<@Positive Long, @NotBlank String> columnOverrides
    ) {
    }

    public record UpdateDataAnalysisTaskRequest(
            @NotNull @Positive Long connectionId,
            @NotNull @Positive Long datasetId,
            @NotNull @Positive Long sheetId,
            @NotBlank @Size(max = 120) String title,
            @NotBlank @Size(max = 2000) String analysisGoal,
            @NotNull @Size(min = 1, max = 3) List<@NotBlank String> expectedOutputs,
            @NotBlank String priority,
            @Size(max = 200) Map<@Positive Long, @NotBlank String> columnOverrides
    ) {
    }

    public record DataAnalysisSummaryResponse(
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

    public record DataAnalysisTaskResponse(
            Long id,
            Long workspaceId,
            Long connectionId,
            String moduleType,
            String title,
            String description,
            String expectedArtifactType,
            String priority,
            String status,
            Long currentRunId,
            String currentRunStatus,
            List<Long> documentIds,
            DataAnalysisSummaryResponse dataAnalysis,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
