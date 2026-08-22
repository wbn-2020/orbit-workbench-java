package com.orbitworkbench.workflow.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.shared.api.PageResult;
import com.orbitworkbench.workflow.api.WorkflowDtos.WorkflowNodeRunResponse;
import com.orbitworkbench.workflow.api.WorkflowDtos.WorkflowRunEventResponse;
import com.orbitworkbench.workflow.api.WorkflowDtos.WorkflowRunRequest;
import com.orbitworkbench.workflow.api.WorkflowDtos.WorkflowRunResponse;
import com.orbitworkbench.workflow.domain.WorkflowNodeRunRecord;
import com.orbitworkbench.workflow.domain.WorkflowRunEventRecord;
import com.orbitworkbench.workflow.domain.WorkflowRunRecord;
import com.orbitworkbench.workflow.domain.WorkflowVersionRecord;
import com.orbitworkbench.workflow.infrastructure.mapper.WorkflowNodeMapper;
import com.orbitworkbench.workflow.infrastructure.mapper.WorkflowNodeRunMapper;
import com.orbitworkbench.workflow.infrastructure.mapper.WorkflowRunMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowRunService {

    private final WorkflowService workflowService;
    private final WorkflowRunMapper runMapper;
    private final WorkflowNodeRunMapper nodeRunMapper;
    private final WorkflowNodeMapper nodeMapper;
    private final WorkflowRunEventService eventService;
    private final WorkflowGraphValidator graphValidator;
    private final ObjectMapper objectMapper;

    public WorkflowRunService(WorkflowService workflowService,
                              WorkflowRunMapper runMapper,
                              WorkflowNodeRunMapper nodeRunMapper,
                              WorkflowNodeMapper nodeMapper,
                              WorkflowRunEventService eventService,
                              WorkflowGraphValidator graphValidator,
                              ObjectMapper objectMapper) {
        this.workflowService = workflowService;
        this.runMapper = runMapper;
        this.nodeRunMapper = nodeRunMapper;
        this.nodeMapper = nodeMapper;
        this.eventService = eventService;
        this.graphValidator = graphValidator;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public WorkflowRunResponse create(Long workflowId, WorkflowRunRequest request) {
        WorkflowVersionRecord version = workflowService.requirePublishedVersion(
                workflowId, request == null ? null : request.versionId());
        WorkflowGraphValidator.Graph graph = workflowService.graph(version);
        List<WorkflowGraphValidator.Node> ordered = graphValidator.topologicalOrder(graph);
        WorkflowGraphValidator.Node start = ordered.stream()
                .filter(node -> "START".equals(node.type()))
                .findFirst()
                .orElseThrow(() -> new ApiException(
                        HttpStatus.BAD_REQUEST, ErrorCode.WORKFLOW_GRAPH_INVALID,
                        "workflow 缺少 START 节点"));

        Instant now = Instant.now();
        WorkflowRunRecord run = new WorkflowRunRecord();
        run.setWorkspaceId(findWorkspaceId(workflowId));
        run.setWorkflowDefinitionId(workflowId);
        run.setWorkflowVersionId(version.getId());
        run.setStatus("QUEUED");
        run.setInputJson(writeJson(request == null || request.input() == null
                ? Map.of() : request.input()));
        run.setCurrentNodeKey(start.key());
        run.setEventSequence(0L);
        run.setCreatedAt(now);
        run.setUpdatedAt(now);
        runMapper.insert(run);

        Map<String, Long> nodeIds = nodeMapper.findByVersionId(version.getId()).stream()
                .collect(java.util.stream.Collectors.toMap(
                        node -> node.getNodeKey(),
                        node -> node.getId(),
                        (left, right) -> left,
                        java.util.LinkedHashMap::new));
        int sequence = 1;
        for (WorkflowGraphValidator.Node node : ordered) {
            WorkflowNodeRunRecord nodeRun = new WorkflowNodeRunRecord();
            nodeRun.setWorkflowRunId(run.getId());
            nodeRun.setWorkflowVersionId(version.getId());
            nodeRun.setWorkflowNodeId(nodeIds.get(node.key()));
            nodeRun.setNodeKey(node.key());
            nodeRun.setNodeType(node.type());
            nodeRun.setSequence(sequence++);
            nodeRun.setStatus("PENDING");
            nodeRun.setCreatedAt(now);
            nodeRun.setUpdatedAt(now);
            nodeRunMapper.insert(nodeRun);
        }
        eventService.append(run.getId(), "workflow.run.queued",
                "workflow 运行已创建，等待执行器接管",
                Map.of("workflowVersionId", version.getId(),
                        "currentNodeKey", start.key()));
        return get(run.getId());
    }

    @Transactional(readOnly = true)
    public WorkflowRunResponse get(Long id) {
        return toResponse(requireRun(id));
    }

    @Transactional(readOnly = true)
    public PageResult<WorkflowRunResponse> findPage(Long workflowId, int page, int size) {
        workflowService.get(workflowId);
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        int offset = (normalizedPage - 1) * normalizedSize;
        List<WorkflowRunResponse> items = runMapper
                .findByWorkflowDefinitionId(workflowId, offset, normalizedSize)
                .stream()
                .map(this::toResponse)
                .toList();
        return new PageResult<>(
                items,
                normalizedPage,
                normalizedSize,
                runMapper.countByWorkflowDefinitionId(workflowId));
    }

    @Transactional(readOnly = true)
    public List<WorkflowNodeRunResponse> nodeRuns(Long id) {
        requireRun(id);
        return nodeRunMapper.findByRunId(id).stream()
                .map(this::toNodeRunResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WorkflowRunEventResponse> events(Long id,
                                                long afterSequence,
                                                int limit) {
        requireRun(id);
        long normalizedAfterSequence = Math.max(afterSequence, 0);
        int normalizedLimit = Math.min(Math.max(limit, 1), 500);
        return eventService.findAfter(id, normalizedAfterSequence, normalizedLimit).stream()
                .map(this::toEventResponse)
                .toList();
    }

    @Transactional
    public WorkflowRunResponse cancel(Long id) {
        WorkflowRunRecord run = runMapper.findByIdForUpdate(id);
        if (run == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.WORKFLOW_RUN_NOT_FOUND,
                    "workflow 运行不存在");
        }
        if (!List.of("QUEUED", "RUNNING", "WAITING_APPROVAL").contains(run.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "当前 workflow 运行状态不允许取消");
        }
        Instant now = Instant.now();
        if (runMapper.updateStatus(
                id, run.getStatus(), "CANCELLED", "CANCELLED",
                "workflow 运行已取消", now, now) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "workflow 运行状态已变化，请刷新后重试");
        }
        nodeRunMapper.cancelPendingByRunId(id);
        eventService.append(id, "workflow.run.cancelled",
                "workflow 运行已取消", Map.of("status", "CANCELLED"));
        return get(id);
    }

    private Long findWorkspaceId(Long workflowId) {
        return workflowService.get(workflowId).workspaceId();
    }

    private WorkflowRunRecord requireRun(Long id) {
        WorkflowRunRecord run = runMapper.findById(id);
        if (run == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.WORKFLOW_RUN_NOT_FOUND,
                    "workflow 运行不存在");
        }
        return run;
    }

    private WorkflowRunResponse toResponse(WorkflowRunRecord run) {
        return new WorkflowRunResponse(
                run.getId(),
                run.getWorkspaceId(),
                run.getWorkflowDefinitionId(),
                run.getWorkflowVersionId(),
                run.getStatus(),
                readJson(run.getInputJson()),
                readJson(run.getOutputJson()),
                run.getCurrentNodeKey(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getErrorCode(),
                run.getErrorSummary(),
                run.getEventSequence(),
                run.getCreatedAt(),
                run.getUpdatedAt());
    }

    private WorkflowNodeRunResponse toNodeRunResponse(WorkflowNodeRunRecord nodeRun) {
        return new WorkflowNodeRunResponse(
                nodeRun.getId(),
                nodeRun.getWorkflowRunId(),
                nodeRun.getWorkflowNodeId(),
                nodeRun.getNodeKey(),
                nodeRun.getNodeType(),
                nodeRun.getSequence(),
                nodeRun.getStatus(),
                nodeRun.getInputSummary(),
                nodeRun.getOutputSummary(),
                nodeRun.getAgentRunId(),
                nodeRun.getAgentRunStepId(),
                nodeRun.getToolCallId(),
                nodeRun.getStartedAt(),
                nodeRun.getFinishedAt(),
                nodeRun.getErrorCode(),
                nodeRun.getErrorSummary(),
                nodeRun.getCreatedAt(),
                nodeRun.getUpdatedAt());
    }

    private WorkflowRunEventResponse toEventResponse(WorkflowRunEventRecord event) {
        return new WorkflowRunEventResponse(
                event.getId(),
                event.getWorkflowRunId(),
                event.getSequence(),
                event.getEventType(),
                event.getEventSummary(),
                readJson(event.getPayloadJson()),
                event.getOccurredAt());
    }

    private Object readJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "workflow 输入无法序列化");
        }
    }
}
