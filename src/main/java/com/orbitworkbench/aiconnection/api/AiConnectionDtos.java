package com.orbitworkbench.aiconnection.api;

import com.orbitworkbench.aiconnection.domain.AiConnectionRecord;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class AiConnectionDtos {
    private AiConnectionDtos() {}

    public record ConnectionRequest(
            @NotBlank @Size(max = 128) String name,
            @NotBlank String providerType,
            @NotBlank String baseUrl,
            @NotBlank String endpointPath,
            @NotBlank String protocol,
            @NotBlank @Size(max = 128) String modelName,
            @Size(max = 512) String apiKey,
            @Min(1000) @Max(120000) Integer timeoutMs,
            Boolean enabled
    ) {}

    public record UpdateConnectionRequest(
            @NotNull @Min(1) Long expectedVersion,
            @NotBlank @Size(max = 128) String name,
            @NotBlank String providerType,
            @NotBlank String baseUrl,
            @NotBlank String endpointPath,
            @NotBlank String protocol,
            @NotBlank @Size(max = 128) String modelName,
            @Size(max = 512) String apiKey,
            @Min(1000) @Max(120000) Integer timeoutMs,
            Boolean enabled
    ) {}

    public record EnabledRequest(boolean enabled,
                                 @NotNull @Min(1) Long expectedVersion) {}

    public record DraftTestRequest(
            @NotBlank String name,
            @NotBlank String providerType,
            @NotBlank String baseUrl,
            @NotBlank String endpointPath,
            @NotBlank String protocol,
            @NotBlank String modelName,
            @NotBlank @Size(max = 512) String apiKey,
            @Min(1000) @Max(120000) Integer timeoutMs,
            boolean streaming,
            @Size(max = 1000) String testPrompt
    ) {}

    public record SavedTestRequest(boolean streaming, @Size(max = 1000) String testPrompt) {}

    public record ConnectionResponse(
            Long id,
            long version,
            String name,
            String providerType,
            String baseUrl,
            String endpointPath,
            String protocol,
            String modelName,
            String credentialMasked,
            boolean enabled,
            int timeoutMs,
            String lastTestStatus,
            Integer lastTestLatencyMs,
            Instant lastTestedAt,
            String lastErrorCode,
            String lastErrorSummary,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static ConnectionResponse from(AiConnectionRecord c) {
            return new ConnectionResponse(c.getId(), c.getConfigurationVersion(),
                    c.getName(), c.getProviderType(), c.getBaseUrl(),
                    c.getEndpointPath(), c.getProtocol(), c.getModelName(),
                    c.getCredentialCiphertext() == null ? null : "****",
                    c.isEnabled(), c.getTimeoutMs(), c.getLastTestStatus(), c.getLastTestLatencyMs(),
                    c.getLastTestedAt(), c.getLastErrorCode(), c.getLastErrorSummary(),
                    c.getCreatedAt(), c.getUpdatedAt());
        }
    }

    public record ConnectionTestResponse(
            String status,
            String protocol,
            boolean streaming,
            Integer latencyMs,
            Integer httpStatus,
            int eventCount,
            boolean doneMarkerReceived,
            String errorCode,
            String errorSummary
    ) {}
}
