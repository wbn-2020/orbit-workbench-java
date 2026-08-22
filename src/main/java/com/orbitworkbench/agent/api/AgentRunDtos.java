package com.orbitworkbench.agent.api;

import java.time.Instant;
import java.util.List;

public final class AgentRunDtos {

    private AgentRunDtos() {
    }

    public record RunCreatedResponse(Long runId,
                                     String status,
                                     String taskStatus,
                                     String command,
                                     Long retryOfRunId,
                                     Long agentVersionId) {
        public RunCreatedResponse(Long runId, String status) {
            this(runId, status, null, "START", null, null);
        }
    }

    public record RunCommandResponse(Long runId,
                                     String status,
                                     String taskStatus,
                                     String command,
                                     Long retryOfRunId,
                                     Long agentVersionId) {
        public static RunCommandResponse from(AgentRunResponse run,
                                               String taskStatus,
                                               String command) {
            return new RunCommandResponse(
                    run.id(),
                    run.status(),
                    taskStatus,
                    command,
                    run.retryOfRunId(),
                    run.agentVersionId()
            );
        }
    }

    public record AgentRunResponse(
            Long id,
            Long taskId,
            Long agentDefinitionId,
            Long agentVersionId,
            Long connectionId,
            String status,
            String currentStep,
            Instant startedAt,
            Instant finishedAt,
            Instant lastHeartbeatAt,
            Long retryOfRunId,
            Long successorRunId,
            String errorCode,
            String errorSummary,
            String traceId,
            Instant createdAt,
            Instant updatedAt,
            Long lastSequence,
            List<ModelCallResponse> modelCalls
    ) {
    }

    public record ModelCallResponse(
            Long id,
            Long connectionId,
            String connectionName,
            String modelName,
            String protocol,
            boolean streaming,
            String status,
            Long latencyMs,
            Integer inputTokens,
            Integer outputTokens,
            String providerRequestId,
            String errorCode,
            String errorSummary,
            Instant startedAt,
            Instant finishedAt
    ) {
    }

    public record RunEventResponse(
            Long id,
            Long runId,
            Long modelCallId,
            long sequence,
            String type,
            String summary,
            Object data,
            Instant occurredAt
    ) {
    }

    public record AgentRunStepResponse(
            Long id,
            Long runId,
            Integer stepNumber,
            String stepType,
            String title,
            String status,
            Long modelCallId,
            Long toolCallId,
            String inputSummary,
            String outputSummary,
            Instant startedAt,
            Instant finishedAt,
            String errorCode,
            String errorSummary
    ) {
    }
}
