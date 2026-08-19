package com.orbitworkbench.aiconnection.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class AiConnectionMetadataDtos {

    private AiConnectionMetadataDtos() {
    }

    public record ModelProfileResponse(
            Long id,
            Long connectionId,
            String modelName,
            String displayName,
            List<String> supportedProtocols,
            Map<String, Object> capabilities,
            Map<String, Object> defaultParameters,
            boolean enabled,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record ConnectionTestResponse(
            Long id,
            Long connectionId,
            String protocol,
            String modelName,
            boolean streaming,
            String status,
            Integer httpStatus,
            Integer latencyMs,
            int eventCount,
            boolean doneMarkerReceived,
            String errorCode,
            String errorSummary,
            long configurationVersion,
            Instant testedAt
    ) {
    }
}
