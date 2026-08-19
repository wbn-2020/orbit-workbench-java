package com.orbitworkbench.task.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class TaskDtos {

    private TaskDtos() {
    }

    public record CreateTaskRequest(
            @NotNull Long workspaceId,
            @NotBlank String moduleType,
            @NotBlank @Size(max = 120) String title,
            @NotBlank @Size(max = 2000) String description,
            @NotBlank @Size(max = 64) String expectedArtifactType,
            @NotBlank String priority,
            List<Long> documentIds,
            @NotNull Long connectionId
    ) {
    }

    public record UpdateTaskRequest(
            @NotBlank String moduleType,
            @NotBlank @Size(max = 120) String title,
            @NotBlank @Size(max = 2000) String description,
            @NotBlank @Size(max = 64) String expectedArtifactType,
            @NotBlank String priority,
            List<Long> documentIds,
            @NotNull Long connectionId
    ) {
    }

    public record TaskResponse(
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
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
