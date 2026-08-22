package com.orbitworkbench.artifactexport.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;

public final class ArtifactExportDtos {

    private ArtifactExportDtos() {
    }

    public record CreateArtifactExportRequest(
            @NotNull @Positive Long artifactVersionId,
            @NotBlank String format
    ) {
    }

    public record ArtifactExportResponse(
            Long id,
            Long artifactId,
            Long artifactVersionId,
            Integer artifactVersion,
            String artifactTitle,
            String artifactType,
            String format,
            String status,
            Long sizeBytes,
            String contentHash,
            String errorCode,
            String errorSummary,
            Long workspaceId,
            Long taskId,
            Long sourceRunId,
            Long datasetId,
            Long sheetId,
            Instant createdAt,
            Instant finishedAt
    ) {
    }
}
