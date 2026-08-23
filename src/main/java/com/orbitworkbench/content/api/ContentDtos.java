package com.orbitworkbench.content.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class ContentDtos {

    private ContentDtos() {
    }

    public record ContentProjectRequest(
            @NotNull Long workspaceId,
            @NotNull Long connectionId,
            @NotBlank @Size(max = 120) String title,
            @NotBlank @Size(max = 2000) String topic,
            @Size(max = 512) String audience,
            @Size(max = 512) String style,
            @NotBlank @Size(max = 32) String outputFormat,
            Long expectedVersion
    ) {
    }

    public record ContentMaterialRequest(
            @NotBlank @Size(max = 32) String sourceType,
            @NotNull Long sourceId,
            @Size(max = 32) String relationType
    ) {
    }

    public record ContentOperationRequest(
            @NotBlank @Size(max = 32) String operation,
            @Size(max = 2000) String instruction,
            Long sourceVersionId
    ) {
    }

    public record ContentMaterialResponse(
            Long id,
            Long contentProjectId,
            String sourceType,
            Long sourceId,
            String relationType,
            Integer sortOrder,
            String sourceTitle,
            Instant createdAt
    ) {
    }

    public record ContentVersionResponse(
            Long id,
            Long contentProjectId,
            Integer versionNumber,
            String operation,
            String status,
            String title,
            String contentFormat,
            String content,
            String requestKey,
            Long taskId,
            Long sourceRunId,
            Long artifactId,
            Long artifactVersionId,
            String errorCode,
            String errorSummary,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record ContentProjectSummaryResponse(
            Long id,
            Long workspaceId,
            Long connectionId,
            String title,
            String topic,
            String outputFormat,
            String status,
            Long version,
            Integer latestVersionNumber,
            String latestVersionStatus,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record ContentProjectResponse(
            Long id,
            Long workspaceId,
            Long connectionId,
            String title,
            String topic,
            String audience,
            String style,
            String outputFormat,
            String status,
            Long version,
            List<ContentMaterialResponse> materials,
            List<ContentVersionResponse> versions,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
