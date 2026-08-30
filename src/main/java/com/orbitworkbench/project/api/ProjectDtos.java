package com.orbitworkbench.project.api;

import com.orbitworkbench.project.domain.ProjectFileRecord;
import com.orbitworkbench.project.domain.ProjectRecord;
import com.orbitworkbench.project.domain.ProjectVersionRecord;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class ProjectDtos {
    private ProjectDtos() {}

    public record ProjectFileResponse(
            Long id,
            String relativePath,
            String mediaType,
            long sizeBytes,
            String status,
            String statusReason
    ) {
        public static ProjectFileResponse from(ProjectFileRecord record) {
            return new ProjectFileResponse(
                    record.getId(), record.getRelativePath(), record.getMediaType(),
                    record.getSizeBytes(), record.getStatus(), record.getStatusReason());
        }
    }

    public record ProjectVersionResponse(
            Long id,
            int versionNumber,
            String sourceType,
            String sourceFileName,
            String status,
            String knowledgeBuildStatus,
            int knowledgeChunkCount,
            int knowledgeBuildAttempts,
            String knowledgeBuildError,
            Instant knowledgeBuiltAt,
            int totalFileCount,
            int parsedFileCount,
            int excludedFileCount,
            int failedFileCount,
            long totalSizeBytes,
            Instant createdAt,
            List<ProjectFileResponse> files
    ) {
        public static ProjectVersionResponse from(ProjectVersionRecord record,
                                                  List<ProjectFileRecord> files) {
            return new ProjectVersionResponse(
                    record.getId(), record.getVersionNumber(), record.getSourceType(),
                    record.getSourceFileName(), record.getStatus(),
                    record.getKnowledgeBuildStatus(), record.getKnowledgeChunkCount(),
                    record.getKnowledgeBuildAttempts(), record.getKnowledgeBuildError(),
                    record.getKnowledgeBuiltAt(), record.getTotalFileCount(),
                    record.getParsedFileCount(), record.getExcludedFileCount(),
                    record.getFailedFileCount(), record.getTotalSizeBytes(), record.getCreatedAt(),
                    files == null ? null : files.stream().map(ProjectFileResponse::from).toList());
        }
    }

    public record ProjectSummaryResponse(
            Long id,
            Long workspaceId,
            String name,
            String status,
            ProjectVersionResponse latestVersion,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static ProjectSummaryResponse from(ProjectRecord project,
                                                  ProjectVersionRecord latest) {
            return new ProjectSummaryResponse(
                    project.getId(), project.getWorkspaceId(), project.getName(), project.getStatus(),
                    latest == null ? null : ProjectVersionResponse.from(latest, null),
                    project.getCreatedAt(), project.getUpdatedAt());
        }
    }

    public record ProjectDetailResponse(
            Long id,
            Long workspaceId,
            String name,
            String status,
            List<ProjectVersionResponse> versions,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record GitHubProjectImportRequest(
            @NotNull Long workspaceId,
            @NotBlank @Size(max = 128) String name,
            @NotBlank @Size(max = 512) String repositoryUrl
    ) {}

    public record GitHubVersionImportRequest(
            @NotBlank @Size(max = 512) String repositoryUrl
    ) {}
}
