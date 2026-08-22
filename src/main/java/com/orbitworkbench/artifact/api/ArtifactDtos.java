package com.orbitworkbench.artifact.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class ArtifactDtos {

    private ArtifactDtos() {
    }

    public record ArtifactSummaryResponse(
            Long id,
            Long taskId,
            String taskTitle,
            String title,
            String artifactType,
            Integer currentVersion,
            Instant updatedAt,
            Instant createdAt
    ) {
    }

    public record ArtifactDetailResponse(
            Long id,
            Long taskId,
            String taskTitle,
            String title,
            String artifactType,
            Integer currentVersion,
            String contentFormat,
            String content,
            Instant updatedAt,
            Instant createdAt
    ) {
    }

    public record ArtifactVersionSummaryResponse(
            Long id,
            Long artifactId,
            Integer version,
            String contentFormat,
            Long sourceRunId,
            String changeSummary,
            Instant createdAt
    ) {
    }

    public record ArtifactVersionResponse(
            Long id,
            Long artifactId,
            Integer version,
            String content,
            String contentFormat,
            Long sourceRunId,
            String changeSummary,
            Instant createdAt
    ) {
    }

    public record UpdateArtifactRequest(
            @NotNull @Min(1) Integer expectedVersion,
            @NotBlank @Size(max = 255) String title,
            @NotNull String content
    ) {
    }
}
