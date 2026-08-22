package com.orbitworkbench.memory.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public final class MemoryDtos {

    private MemoryDtos() {
    }

    public record MemoryRequest(
            @NotNull Long workspaceId,
            @NotBlank @Size(max = 32) String memoryType,
            @NotEmpty Map<String, Object> content,
            @NotBlank @Size(max = 32) String sourceType,
            Long sourceId,
            @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal confidence,
            Instant expiresAt,
            Long expectedVersion
    ) {
    }

    public record MemoryCandidateRequest(
            @NotNull Long workspaceId,
            @NotBlank @Size(max = 32) String memoryType,
            @NotEmpty Map<String, Object> content,
            @NotBlank @Size(max = 32) String sourceType,
            Long sourceId,
            @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal confidence,
            Instant expiresAt
    ) {
    }

    public record MemoryResponse(
            Long id,
            Long workspaceId,
            String memoryType,
            Map<String, Object> content,
            String sourceType,
            Long sourceId,
            BigDecimal confidence,
            Instant expiresAt,
            String status,
            Long version,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record MemoryCandidateResponse(
            Long id,
            Long workspaceId,
            String memoryType,
            Map<String, Object> content,
            String sourceType,
            Long sourceId,
            BigDecimal confidence,
            Instant expiresAt,
            String status,
            Long version,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record MemoryCommandRequest(Long expectedVersion) {
    }
}
