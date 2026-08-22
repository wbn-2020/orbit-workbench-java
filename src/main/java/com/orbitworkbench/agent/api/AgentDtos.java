package com.orbitworkbench.agent.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class AgentDtos {

    private AgentDtos() {
    }

    public record AgentRequest(
            @NotNull Long workspaceId,
            @NotBlank @Size(max = 128) String code,
            @NotBlank @Size(max = 128) String name,
            @Size(max = 512) String description,
            Long connectionId,
            Long modelProfileId,
            Long promptVersionId,
            String promptContent,
            Object promptVariables,
            Map<String, Object> configuration,
            Long expectedVersion
    ) {
    }

    public record AgentVersionRequest(
            Long connectionId,
            Long modelProfileId,
            Long promptVersionId,
            String promptContent,
            Object promptVariables,
            Map<String, Object> configuration,
            Long expectedVersion
    ) {
    }

    public record AgentCommandRequest(Long expectedVersion, Long versionId) {
    }

    public record AgentResponse(
            Long id,
            Long workspaceId,
            String code,
            String name,
            String description,
            String status,
            Long publishedVersionId,
            Long version,
            Long draftVersionId,
            List<AgentVersionResponse> versions,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record AgentVersionResponse(
            Long id,
            Long agentDefinitionId,
            Integer versionNumber,
            String status,
            Long connectionId,
            String connectionName,
            Long modelProfileId,
            String modelName,
            Long promptVersionId,
            Integer promptVersionNumber,
            String promptContent,
            Object promptVariables,
            Map<String, Object> configuration,
            Instant publishedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
