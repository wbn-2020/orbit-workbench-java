package com.orbitworkbench.dataset.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class DatasetDtos {

    private DatasetDtos() {
    }

    public record DatasetSummaryResponse(
            Long id,
            Long workspaceId,
            Long documentId,
            String name,
            String format,
            String status,
            Long activeSheetId,
            String activeSheetName,
            Long rowCount,
            Integer columnCount,
            String errorCode,
            String errorSummary,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record DatasetDetailResponse(
            Long id,
            Long workspaceId,
            Long documentId,
            String name,
            String format,
            String status,
            Long activeSheetId,
            Long rowCount,
            Integer columnCount,
            Integer profileVersion,
            Long version,
            String errorCode,
            String errorSummary,
            List<DatasetSheetResponse> sheets,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record DatasetSheetResponse(
            Long id,
            Long datasetId,
            Integer sheetIndex,
            String sheetName,
            Long rowCount,
            Integer columnCount,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record DatasetColumnResponse(
            Long id,
            Long datasetId,
            Long sheetId,
            Integer ordinalPosition,
            String columnName,
            String normalizedName,
            String inferredType,
            String effectiveType,
            boolean nullable,
            List<String> sampleValues,
            Long version,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record DatasetPreviewResponse(
            List<String> columns,
            List<List<String>> rows,
            int offset,
            int limit,
            boolean hasMore,
            long totalRows
    ) {
    }

    public record DatasetProfileResponse(
            Long datasetId,
            Long sheetId,
            Integer profileVersion,
            Long rowCount,
            Integer columnCount,
            Map<String, Object> summary,
            Map<String, Object> quality,
            Instant createdAt
    ) {
    }

    public record UpdateDatasetColumnRequest(
            @NotNull @Min(1) Long expectedVersion,
            @NotBlank String effectiveType
    ) {
    }
}
