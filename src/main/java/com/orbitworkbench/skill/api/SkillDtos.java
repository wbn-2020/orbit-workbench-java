package com.orbitworkbench.skill.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class SkillDtos {

    private SkillDtos() {
    }

    public record SkillRequest(
            @NotNull Long workspaceId,
            @NotBlank @Size(max = 128) String skillCode,
            @NotBlank @Size(max = 128) String name,
            @Size(max = 512) String description,
            @NotNull @Valid SkillVersionRequest version,
            Long expectedVersion
    ) {
    }

    public record SkillVersionRequest(
            @NotNull Long promptVersionId,
            Map<String, Object> inputSchema,
            @NotBlank @Size(max = 64) String outputType,
            Map<String, Object> runtimeLimits,
            List<@NotNull Long> toolVersionIds,
            Long expectedVersion
    ) {
    }

    public record SkillCommandRequest(
            Long expectedVersion,
            Long versionId
    ) {
    }

    public record SkillResponse(
            Long id,
            Long workspaceId,
            String skillCode,
            String name,
            String description,
            Long publishedVersionId,
            String status,
            Long lockVersion,
            List<SkillVersionResponse> versions,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record SkillVersionResponse(
            Long id,
            Long skillDefinitionId,
            Integer versionNumber,
            String status,
            Long promptVersionId,
            Integer promptVersionNumber,
            Object inputSchema,
            String outputType,
            Object runtimeLimits,
            List<Long> toolVersionIds,
            Instant publishedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
