package com.orbitworkbench.tool.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class ToolCatalogDtos {

    private ToolCatalogDtos() {
    }

    public record ToolCatalogResponse(
            Long id,
            String toolCode,
            String name,
            String description,
            String handlerType,
            Long publishedVersionId,
            String status,
            Long lockVersion,
            List<ToolVersionResponse> versions,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record ToolVersionResponse(
            Long id,
            Long toolCatalogId,
            Integer versionNumber,
            String status,
            Object inputSchema,
            Object outputSchema,
            String riskLevel,
            boolean requiresConfirmation,
            Integer timeoutMs,
            Integer maxResultBytes,
            Object capabilities,
            Instant publishedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record ToolVersionRequest(
            Map<String, Object> inputSchema,
            Map<String, Object> outputSchema,
            @NotBlank @jakarta.validation.constraints.Size(max = 32) String riskLevel,
            boolean requiresConfirmation,
            @NotNull @Min(1) @Max(120000) Integer timeoutMs,
            @NotNull @Min(1) @Max(1048576) Integer maxResultBytes,
            Map<String, Object> capabilities,
            Long expectedVersion
    ) {
    }

    public record ToolCommandRequest(Long expectedVersion, Long versionId) {
    }
}
