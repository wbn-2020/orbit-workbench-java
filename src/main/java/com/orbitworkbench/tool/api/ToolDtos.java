package com.orbitworkbench.tool.api;

import java.time.Duration;
import java.time.Instant;

public final class ToolDtos {

    private ToolDtos() {
    }

    public record ToolDefinitionResponse(
            Long id,
            String toolCode,
            String name,
            String description,
            Integer toolVersion,
            Object inputSchema,
            Object outputSchema,
            String riskLevel,
            boolean requiresConfirmation,
            Integer timeoutMs,
            Integer maxResultBytes,
            boolean enabled,
            Instant updatedAt
    ) {
    }

    public record ToolCallResponse(
            Long id,
            Long runId,
            Long stepId,
            Long modelCallId,
            String toolCode,
            Integer toolVersion,
            Long toolVersionId,
            String status,
            String argumentsSummary,
            String resultSummary,
            Integer resultSizeBytes,
            Long durationMs,
            String errorCode,
            String errorSummary,
            Instant startedAt,
            Instant finishedAt,
            Instant createdAt
    ) {
        public static Long duration(Instant startedAt, Instant finishedAt) {
            return startedAt == null || finishedAt == null
                    ? null : Duration.between(startedAt, finishedAt).toMillis();
        }
    }
}
