package com.orbitworkbench.workflow.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class WorkflowDtos {

    private WorkflowDtos() {
    }

    public record WorkflowRequest(
            @NotNull Long workspaceId,
            @NotBlank @Size(max = 128) String code,
            @NotBlank @Size(max = 128) String name,
            @Size(max = 512) String description,
            @NotNull @Valid WorkflowGraphRequest graph,
            Map<String, Object> metadata
    ) {
    }

    public record WorkflowUpdateRequest(
            @NotBlank @Size(max = 128) String name,
            @Size(max = 512) String description,
            @NotNull Long expectedVersion,
            @Valid WorkflowGraphRequest graph,
            Map<String, Object> metadata
    ) {
    }

    public record WorkflowVersionRequest(
            @NotNull @Valid WorkflowGraphRequest graph,
            Map<String, Object> metadata
    ) {
    }

    public record WorkflowGraphRequest(
            @NotNull List<@Valid WorkflowNodeRequest> nodes,
            @NotNull List<@Valid WorkflowEdgeRequest> edges
    ) {
    }

    public record WorkflowNodeRequest(
            @NotBlank @Size(max = 64) String key,
            @NotBlank @Size(max = 16) String type,
            @NotBlank @Size(max = 128) String name,
            Map<String, Object> config,
            Map<String, Object> position
    ) {
    }

    public record WorkflowEdgeRequest(
            @NotBlank @Size(max = 64) String from,
            @NotBlank @Size(max = 64) String to,
            @Size(max = 32) String branch,
            Integer order
    ) {
    }

    public record WorkflowCommandRequest(Long expectedVersion,
                                         Long versionId) {
    }

    public record WorkflowRunRequest(
            Long versionId,
            Map<String, Object> input
    ) {
    }

    public record WorkflowResponse(
            Long id,
            Long workspaceId,
            String code,
            String name,
            String description,
            String status,
            Long publishedVersionId,
            Long version,
            List<WorkflowVersionSummaryResponse> versions,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record WorkflowVersionSummaryResponse(
            Long id,
            Long workflowDefinitionId,
            Integer versionNumber,
            String status,
            Instant publishedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record WorkflowVersionResponse(
            Long id,
            Long workflowDefinitionId,
            Integer versionNumber,
            String status,
            Object metadata,
            List<WorkflowNodeResponse> nodes,
            List<WorkflowEdgeResponse> edges,
            Instant publishedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record WorkflowNodeResponse(
            Long id,
            String key,
            String type,
            String name,
            Object config,
            Object position
    ) {
    }

    public record WorkflowEdgeResponse(
            Long id,
            String from,
            String to,
            String branch,
            Integer order
    ) {
    }

    public record WorkflowValidationResponse(
            boolean valid,
            List<String> errors
    ) {
    }

    public record WorkflowRunResponse(
            Long id,
            Long workspaceId,
            Long workflowDefinitionId,
            Long workflowVersionId,
            String status,
            Object input,
            Object output,
            String currentNodeKey,
            Instant startedAt,
            Instant finishedAt,
            String errorCode,
            String errorSummary,
            long eventSequence,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record WorkflowNodeRunResponse(
            Long id,
            Long workflowRunId,
            Long workflowNodeId,
            String nodeKey,
            String nodeType,
            Integer sequence,
            String status,
            String inputSummary,
            String outputSummary,
            Long agentRunId,
            Long agentRunStepId,
            Long toolCallId,
            Instant startedAt,
            Instant finishedAt,
            String errorCode,
            String errorSummary,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record WorkflowRunEventResponse(
            Long id,
            Long workflowRunId,
            long sequence,
            String type,
            String summary,
            Object data,
            Instant occurredAt
    ) {
    }
}
