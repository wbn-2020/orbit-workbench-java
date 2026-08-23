package com.orbitworkbench.workflow.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.ai.application.AiToolCall;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.tool.application.ToolExecutionContext;
import com.orbitworkbench.tool.application.ToolExecutionOutcome;
import com.orbitworkbench.tool.application.ToolExecutionService;
import com.orbitworkbench.tool.domain.ToolVersionRecord;
import com.orbitworkbench.tool.infrastructure.mapper.ToolVersionMapper;
import com.orbitworkbench.mcp.domain.McpServerRecord;
import com.orbitworkbench.mcp.domain.McpToolRecord;
import com.orbitworkbench.mcp.infrastructure.mapper.McpServerMapper;
import com.orbitworkbench.mcp.infrastructure.mapper.McpToolMapper;
import com.orbitworkbench.memory.application.MemoryProposalSummary;
import com.orbitworkbench.memory.application.MemoryService;
import com.orbitworkbench.workflow.domain.WorkflowNodeRunRecord;
import com.orbitworkbench.workflow.domain.WorkflowRunRecord;
import com.orbitworkbench.workflow.domain.WorkflowVersionRecord;
import com.orbitworkbench.workflow.infrastructure.mapper.WorkflowNodeRunMapper;
import com.orbitworkbench.workflow.infrastructure.mapper.WorkflowRunMapper;
import com.orbitworkbench.workflow.infrastructure.mapper.WorkflowVersionMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Executes the deliberately small first Workflow runtime slice:
 * START -> TOOL(MCP) -> END.
 */
@Component
public class WorkflowRunWorker {

    private final WorkflowRunMapper runMapper;
    private final WorkflowNodeRunMapper nodeRunMapper;
    private final WorkflowVersionMapper versionMapper;
    private final ToolVersionMapper toolVersionMapper;
    private final McpServerMapper mcpServerMapper;
    private final McpToolMapper mcpToolMapper;
    private final WorkflowService workflowService;
    private final WorkflowGraphValidator graphValidator;
    private final WorkflowRunEventService eventService;
    private final ToolExecutionService toolExecutionService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final MemoryService memoryService;

    public WorkflowRunWorker(WorkflowRunMapper runMapper,
                             WorkflowNodeRunMapper nodeRunMapper,
                             WorkflowVersionMapper versionMapper,
                             ToolVersionMapper toolVersionMapper,
                             McpServerMapper mcpServerMapper,
                             McpToolMapper mcpToolMapper,
                             WorkflowService workflowService,
                             WorkflowGraphValidator graphValidator,
                             WorkflowRunEventService eventService,
                             ToolExecutionService toolExecutionService,
                             ObjectMapper objectMapper,
                             TransactionTemplate transactionTemplate,
                             MemoryService memoryService) {
        this.runMapper = runMapper;
        this.nodeRunMapper = nodeRunMapper;
        this.versionMapper = versionMapper;
        this.toolVersionMapper = toolVersionMapper;
        this.mcpServerMapper = mcpServerMapper;
        this.mcpToolMapper = mcpToolMapper;
        this.workflowService = workflowService;
        this.graphValidator = graphValidator;
        this.eventService = eventService;
        this.toolExecutionService = toolExecutionService;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.memoryService = memoryService;
    }

    public void execute(Long workflowRunId) {
        WorkflowRunRecord run = runMapper.findById(workflowRunId);
        if (run == null || "CANCELLED".equals(run.getStatus())) {
            return;
        }
        if (runMapper.markRunning(workflowRunId, Instant.now()) != 1) {
            run = runMapper.findById(workflowRunId);
            if (run == null || !"RUNNING".equals(run.getStatus())) {
                return;
            }
        } else {
            eventService.append(workflowRunId, "workflow.run.started",
                    "workflow 运行已开始", Map.of("status", "RUNNING"));
        }

        WorkflowNodeRunRecord activeNodeRun = null;
        try {
            WorkflowVersionRecord version = versionMapper.findById(run.getWorkflowVersionId());
            if (version == null) {
                throw executionFailure("Workflow 版本不存在");
            }
            WorkflowGraphValidator.Graph graph = workflowService.graph(version);
            WorkflowGraphValidator.ValidationResult validation =
                    graphValidator.validate(graph);
            if (!validation.valid()) {
                throw executionFailure("Workflow 图校验失败");
            }
            List<WorkflowGraphValidator.Node> ordered =
                    graphValidator.topologicalOrder(graph);
            requireSupportedShape(ordered);

            Map<String, WorkflowNodeRunRecord> nodeRuns = nodeRunMapper
                    .findByRunId(workflowRunId)
                    .stream()
                    .collect(java.util.stream.Collectors.toMap(
                            WorkflowNodeRunRecord::getNodeKey,
                            nodeRun -> nodeRun,
                            (left, right) -> left,
                            LinkedHashMap::new));
            WorkflowGraphValidator.Node start = ordered.get(0);
            activeNodeRun = requireNodeRun(nodeRuns, start.key());
            runStartNode(workflowRunId, activeNodeRun);

            WorkflowGraphValidator.Node tool = ordered.get(1);
            activeNodeRun = requireNodeRun(nodeRuns, tool.key());
            com.fasterxml.jackson.databind.JsonNode arguments = toolArguments(tool);
            ToolVersionRecord toolVersion = requireMcpToolVersion(
                    tool, run.getWorkspaceId());
            ToolExecutionOutcome outcome = runToolNode(
                    workflowRunId, run, activeNodeRun, tool, toolVersion, arguments);

            WorkflowGraphValidator.Node end = ordered.get(2);
            activeNodeRun = requireNodeRun(nodeRuns, end.key());
            runEndNode(workflowRunId, activeNodeRun);
            String outputJson = writeJson(Map.of(
                    "toolCallId", outcome.toolCall().getId(),
                    "result", outcome.result()));
            succeed(workflowRunId, outputJson);
            MemoryProposalSummary memoryProposal = proposeMemoryCandidates(
                    () -> memoryService.proposeFromWorkflowOutput(
                            workflowRunId,
                            outputJson));
            publishMemoryProposalEvent(workflowRunId, memoryProposal);
        } catch (ApiException exception) {
            if (exception.getErrorCode() == ErrorCode.CANCELLED) {
                cancel(workflowRunId, activeNodeRun);
                return;
            }
            fail(workflowRunId, activeNodeRun, exception.getErrorCode(),
                    safeSummary(exception.getMessage(), "Workflow 执行失败"));
        } catch (RuntimeException exception) {
            fail(workflowRunId, activeNodeRun, ErrorCode.WORKFLOW_EXECUTION_FAILED,
                    "Workflow 执行失败");
        }
    }

    private void runStartNode(Long runId, WorkflowNodeRunRecord nodeRun) {
        startNode(runId, nodeRun);
        completeNode(runId, nodeRun, "Workflow 起始节点");
    }

    private ToolExecutionOutcome runToolNode(
            Long runId,
            WorkflowRunRecord run,
            WorkflowNodeRunRecord nodeRun,
            WorkflowGraphValidator.Node node,
            ToolVersionRecord toolVersion,
            com.fasterxml.jackson.databind.JsonNode arguments) {
        startNode(runId, nodeRun);
        ToolExecutionContext context = ToolExecutionContext.forWorkflow(
                runId,
                nodeRun.getId(),
                run.getWorkspaceId(),
                Map.of(toolVersion.getToolCode(), toolVersion.getId()),
                () -> requireRunning(runId));
        ToolExecutionOutcome outcome = toolExecutionService.execute(
                context,
                new AiToolCall(
                        "workflow-" + runId + "-" + node.key(),
                        toolVersion.getToolCode(),
                        writeJson(arguments)));
        completeNode(runId, nodeRun, safeSummary(
                outcome.toolCall().getResultSummary(), "MCP Tool 调用完成"));
        return outcome;
    }

    private void runEndNode(Long runId, WorkflowNodeRunRecord nodeRun) {
        startNode(runId, nodeRun);
        completeNode(runId, nodeRun, "Workflow 结束节点");
    }

    private void startNode(Long runId, WorkflowNodeRunRecord nodeRun) {
        if (runMapper.updateCurrentNode(
                runId, nodeRun.getNodeKey(), Instant.now()) != 1) {
            throw executionFailure("Workflow 运行状态已变化");
        }
        if (nodeRunMapper.markRunning(nodeRun.getId(), Instant.now()) != 1) {
            throw executionFailure("Workflow 节点状态已变化");
        }
        eventService.append(runId, "workflow.node.started",
                "节点已开始: " + nodeRun.getNodeKey(),
                Map.of("nodeRunId", nodeRun.getId(),
                        "nodeKey", nodeRun.getNodeKey(),
                        "nodeType", nodeRun.getNodeType()));
    }

    private void completeNode(Long runId,
                              WorkflowNodeRunRecord nodeRun,
                              String summary) {
        String safeSummary = safeSummary(summary, "Workflow 节点已完成");
        if (nodeRunMapper.markSucceeded(
                nodeRun.getId(), safeSummary, Instant.now()) != 1) {
            throw executionFailure("Workflow 节点状态已变化");
        }
        eventService.append(runId, "workflow.node.completed", safeSummary,
                Map.of("nodeRunId", nodeRun.getId(),
                        "nodeKey", nodeRun.getNodeKey(),
                        "status", "SUCCEEDED"));
    }

    private void succeed(Long runId, String outputJson) {
        Instant now = Instant.now();
        if (runMapper.markSucceeded(runId, outputJson, now, now) != 1) {
            return;
        }
        eventService.append(runId, "workflow.run.completed",
                "workflow 运行已完成", Map.of("status", "SUCCEEDED"));
    }

    private MemoryProposalSummary proposeMemoryCandidates(
            java.util.function.Supplier<MemoryProposalSummary> proposal) {
        try {
            return proposal.get();
        } catch (RuntimeException exception) {
            return MemoryProposalSummary.failed();
        }
    }

    private void publishMemoryProposalEvent(
            Long runId,
            MemoryProposalSummary summary) {
        if (!summary.blockFound() && summary.proposedCount() == 0
                && summary.rejectedCount() == 0) {
            return;
        }
        eventService.append(
                runId,
                "memory.candidates.proposed",
                "运行输出中的记忆候选已处理",
                Map.of(
                        "proposedCount", summary.proposedCount(),
                        "rejectedCount", summary.rejectedCount()));
    }

    private void fail(Long runId,
                      WorkflowNodeRunRecord activeNodeRun,
                      ErrorCode errorCode,
                      String summary) {
        transactionTemplate.executeWithoutResult(status -> {
            WorkflowRunRecord current = runMapper.findByIdForUpdate(runId);
            if (current == null || List.of(
                    "SUCCEEDED", "FAILED", "CANCELLED").contains(current.getStatus())) {
                return;
            }
            Instant now = Instant.now();
            if (activeNodeRun != null
                    && nodeRunMapper.markFailed(
                    activeNodeRun.getId(), errorCode.name(), summary, now) == 1) {
                eventService.append(runId, "workflow.node.failed", summary,
                        Map.of("nodeRunId", activeNodeRun.getId(),
                                "nodeKey", activeNodeRun.getNodeKey(),
                                "errorCode", errorCode.name()));
            }
            nodeRunMapper.cancelPendingByRunId(runId);
            runMapper.markFailed(runId, errorCode.name(), summary, now, now);
            eventService.append(runId, "workflow.run.failed", summary,
                    Map.of("status", "FAILED",
                            "errorCode", errorCode.name()));
        });
    }

    private void cancel(Long runId, WorkflowNodeRunRecord activeNodeRun) {
        transactionTemplate.executeWithoutResult(status -> {
            WorkflowRunRecord current = runMapper.findByIdForUpdate(runId);
            if (current == null || List.of(
                    "SUCCEEDED", "FAILED", "CANCELLED").contains(current.getStatus())) {
                return;
            }
            nodeRunMapper.cancelPendingByRunId(runId);
            if (runMapper.updateStatus(
                    runId, current.getStatus(), "CANCELLED",
                    ErrorCode.CANCELLED.name(), "Workflow 运行已取消",
                    Instant.now(), Instant.now()) == 1) {
                eventService.append(runId, "workflow.run.cancelled",
                        "Workflow 运行已取消",
                        Map.of("status", "CANCELLED"));
            }
        });
    }

    private void requireRunning(Long runId) {
        WorkflowRunRecord run = runMapper.findById(runId);
        if (run == null || !"RUNNING".equals(run.getStatus())) {
            throw new ApiException(HttpStatus.REQUEST_TIMEOUT,
                    ErrorCode.CANCELLED, "Workflow 运行已取消");
        }
    }

    private WorkflowNodeRunRecord requireNodeRun(
            Map<String, WorkflowNodeRunRecord> nodeRuns,
            String nodeKey) {
        WorkflowNodeRunRecord nodeRun = nodeRuns.get(nodeKey);
        if (nodeRun == null) {
            throw executionFailure("Workflow 节点运行记录不存在: " + nodeKey);
        }
        return nodeRun;
    }

    private void requireSupportedShape(List<WorkflowGraphValidator.Node> ordered) {
        if (ordered.size() != 3
                || !"START".equals(ordered.get(0).type())
                || !"TOOL".equals(ordered.get(1).type())
                || !"END".equals(ordered.get(2).type())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    ErrorCode.WORKFLOW_EXECUTION_UNSUPPORTED,
                    "当前仅支持 START -> TOOL -> END Workflow");
        }
    }

    private ToolVersionRecord requireMcpToolVersion(
            WorkflowGraphValidator.Node node,
            Long workspaceId) {
        Map<String, Object> config = node.config() == null ? Map.of() : node.config();
        String toolCode = config.get("toolCode") instanceof String text
                ? text : null;
        Integer toolVersionNumber = config.get("toolVersion") instanceof Number number
                ? number.intValue() : null;
        McpToolRecord mcpTool = mcpToolMapper.findByToolCode(toolCode);
        if (mcpTool == null || !mcpTool.isEnabled()) {
            throw new ApiException(HttpStatus.CONFLICT,
                    ErrorCode.WORKFLOW_EXECUTION_UNSUPPORTED,
                    "当前仅支持已启用 MCP Tool: " + node.key());
        }
        McpServerRecord server = mcpServerMapper.findById(mcpTool.getMcpServerId());
        if (server == null || !workspaceId.equals(server.getWorkspaceId())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    ErrorCode.WORKFLOW_EXECUTION_UNSUPPORTED,
                    "MCP Tool 不属于当前 Workflow 工作空间: " + node.key());
        }
        ToolVersionRecord version = toolVersionMapper
                .findPublishedByCodeAndVersion(toolCode, toolVersionNumber);
        if (version == null) {
            throw new ApiException(HttpStatus.CONFLICT,
                    ErrorCode.TOOL_VERSION_INVALID,
                    "Workflow 引用的 ToolVersion 不可执行: " + node.key());
        }
        return version;
    }

    private com.fasterxml.jackson.databind.JsonNode toolArguments(
            WorkflowGraphValidator.Node node) {
        Map<String, Object> config = node.config() == null ? Map.of() : node.config();
        Object arguments = config.get("arguments");
        try {
            String json = objectMapper.writeValueAsString(
                    arguments == null ? Map.of() : arguments);
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw executionFailure("Workflow 工具参数无法序列化: " + node.key());
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw executionFailure("Workflow 输出无法序列化");
        }
    }

    private ApiException executionFailure(String summary) {
        return new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCode.WORKFLOW_EXECUTION_FAILED, summary);
    }

    private String safeSummary(String value, String fallback) {
        String normalized = value == null || value.isBlank()
                ? fallback : value.replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .trim();
        return normalized.length() <= 512
                ? normalized : normalized.substring(0, 512);
    }
}
