package com.orbitworkbench.mcp.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class McpDtos {

    private McpDtos() {
    }

    public record McpServerRequest(
            @NotNull Long workspaceId,
            @NotBlank @Size(max = 64) String serverCode,
            @NotBlank @Size(max = 128) String name,
            @NotBlank String transport,
            @NotBlank @Size(max = 1024) String endpointUrl,
            @Size(max = 256) String credentialRef,
            boolean allowPrivateNetwork,
            Long expectedVersion
    ) {
    }

    public record McpServerResponse(
            Long id,
            Long workspaceId,
            String serverCode,
            String name,
            String transport,
            String endpointUrl,
            boolean hasCredentialRef,
            boolean allowPrivateNetwork,
            String status,
            String syncStatus,
            Instant lastSyncAt,
            String lastErrorCode,
            String lastErrorSummary,
            Long lockVersion,
            List<McpToolResponse> tools,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record McpToolResponse(
            Long id,
            Long mcpServerId,
            String toolCode,
            String toolName,
            String title,
            String description,
            Long toolCatalogId,
            Long toolVersionId,
            Integer versionNumber,
            boolean enabled,
            String catalogStatus,
            Object inputSchema,
            Object outputSchema,
            String riskLevel,
            boolean requiresConfirmation,
            Integer timeoutMs,
            Integer maxResultBytes,
            Instant updatedAt
    ) {
    }
}
