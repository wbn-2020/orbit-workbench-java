package com.orbitworkbench.workflow.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.agent.domain.AgentDefinitionDetailRecord;
import com.orbitworkbench.agent.domain.AgentVersionRecord;
import com.orbitworkbench.agent.infrastructure.mapper.AgentDefinitionMapper;
import com.orbitworkbench.agent.infrastructure.mapper.AgentVersionMapper;
import com.orbitworkbench.mcp.domain.McpServerRecord;
import com.orbitworkbench.mcp.domain.McpToolRecord;
import com.orbitworkbench.mcp.infrastructure.mapper.McpServerMapper;
import com.orbitworkbench.mcp.infrastructure.mapper.McpToolMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.shared.api.PageResult;
import com.orbitworkbench.tool.domain.ToolVersionRecord;
import com.orbitworkbench.tool.infrastructure.mapper.ToolVersionMapper;
import com.orbitworkbench.workflow.api.WorkflowDtos.WorkflowCommandRequest;
import com.orbitworkbench.workflow.api.WorkflowDtos.WorkflowEdgeRequest;
import com.orbitworkbench.workflow.api.WorkflowDtos.WorkflowEdgeResponse;
import com.orbitworkbench.workflow.api.WorkflowDtos.WorkflowGraphRequest;
import com.orbitworkbench.workflow.api.WorkflowDtos.WorkflowNodeRequest;
import com.orbitworkbench.workflow.api.WorkflowDtos.WorkflowNodeResponse;
import com.orbitworkbench.workflow.api.WorkflowDtos.WorkflowRequest;
import com.orbitworkbench.workflow.api.WorkflowDtos.WorkflowResponse;
import com.orbitworkbench.workflow.api.WorkflowDtos.WorkflowUpdateRequest;
import com.orbitworkbench.workflow.api.WorkflowDtos.WorkflowValidationResponse;
import com.orbitworkbench.workflow.api.WorkflowDtos.WorkflowVersionRequest;
import com.orbitworkbench.workflow.api.WorkflowDtos.WorkflowVersionResponse;
import com.orbitworkbench.workflow.api.WorkflowDtos.WorkflowVersionSummaryResponse;
import com.orbitworkbench.workflow.domain.WorkflowDefinitionRecord;
import com.orbitworkbench.workflow.domain.WorkflowEdgeRecord;
import com.orbitworkbench.workflow.domain.WorkflowNodeRecord;
import com.orbitworkbench.workflow.domain.WorkflowVersionRecord;
import com.orbitworkbench.workflow.infrastructure.mapper.WorkflowDefinitionMapper;
import com.orbitworkbench.workflow.infrastructure.mapper.WorkflowEdgeMapper;
import com.orbitworkbench.workflow.infrastructure.mapper.WorkflowNodeMapper;
import com.orbitworkbench.workflow.infrastructure.mapper.WorkflowVersionMapper;
import com.orbitworkbench.workspace.infrastructure.mapper.WorkspaceMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowService {

    private final WorkflowDefinitionMapper definitionMapper;
    private final WorkflowVersionMapper versionMapper;
    private final WorkflowNodeMapper nodeMapper;
    private final WorkflowEdgeMapper edgeMapper;
    private final WorkspaceMapper workspaceMapper;
    private final AgentDefinitionMapper agentDefinitionMapper;
    private final AgentVersionMapper agentVersionMapper;
    private final ToolVersionMapper toolVersionMapper;
    private final McpServerMapper mcpServerMapper;
    private final McpToolMapper mcpToolMapper;
    private final WorkflowGraphValidator graphValidator;
    private final ObjectMapper objectMapper;

    public WorkflowService(WorkflowDefinitionMapper definitionMapper,
                           WorkflowVersionMapper versionMapper,
                           WorkflowNodeMapper nodeMapper,
                           WorkflowEdgeMapper edgeMapper,
                           WorkspaceMapper workspaceMapper,
                           AgentDefinitionMapper agentDefinitionMapper,
                           AgentVersionMapper agentVersionMapper,
                           ToolVersionMapper toolVersionMapper,
                           McpServerMapper mcpServerMapper,
                           McpToolMapper mcpToolMapper,
                           WorkflowGraphValidator graphValidator,
                           ObjectMapper objectMapper) {
        this.definitionMapper = definitionMapper;
        this.versionMapper = versionMapper;
        this.nodeMapper = nodeMapper;
        this.edgeMapper = edgeMapper;
        this.workspaceMapper = workspaceMapper;
        this.agentDefinitionMapper = agentDefinitionMapper;
        this.agentVersionMapper = agentVersionMapper;
        this.toolVersionMapper = toolVersionMapper;
        this.mcpServerMapper = mcpServerMapper;
        this.mcpToolMapper = mcpToolMapper;
        this.graphValidator = graphValidator;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public WorkflowResponse create(WorkflowRequest request) {
        requireWorkspace(request.workspaceId());
        String code = requiredText(request.code(), "code", 128);
        if (definitionMapper.findByWorkspaceCode(request.workspaceId(), code) != null) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.WORKFLOW_PUBLISH_CONFLICT,
                    "当前工作空间已存在相同 workflow code");
        }
        WorkflowGraphValidator.Graph graph = toGraph(request.graph());
        validateGraph(graph, request.workspaceId());

        Instant now = Instant.now();
        WorkflowDefinitionRecord definition = new WorkflowDefinitionRecord();
        definition.setWorkspaceId(request.workspaceId());
        definition.setCode(code);
        definition.setName(requiredText(request.name(), "name", 128));
        definition.setDescription(normalizeOptional(request.description(), 512));
        definition.setStatus("ACTIVE");
        definition.setVersion(1L);
        definition.setCreatedAt(now);
        definition.setUpdatedAt(now);
        definitionMapper.insert(definition);

        WorkflowVersionRecord version = newVersion(
                definition.getId(), 1, request.metadata(), now);
        versionMapper.insert(version);
        persistGraph(version.getId(), graph, now);
        return get(definition.getId());
    }

    @Transactional(readOnly = true)
    public List<WorkflowResponse> findByWorkspaceId(Long workspaceId) {
        requireWorkspace(workspaceId);
        return definitionMapper.findByWorkspaceId(workspaceId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResult<WorkflowResponse> findPageByWorkspaceId(
            Long workspaceId,
            String status,
            int page,
            int size) {
        requireWorkspace(workspaceId);
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        int offset = (normalizedPage - 1) * normalizedSize;
        String normalizedStatus = normalizeListStatus(status);
        List<WorkflowResponse> items = definitionMapper
                .findPageByWorkspaceId(workspaceId, normalizedStatus, offset, normalizedSize)
                .stream()
                .map(this::toResponse)
                .toList();
        return new PageResult<>(
                items,
                normalizedPage,
                normalizedSize,
                definitionMapper.countByWorkspaceId(workspaceId, normalizedStatus));
    }

    @Transactional(readOnly = true)
    public WorkflowResponse get(Long id) {
        return toResponse(requireDefinition(id));
    }

    @Transactional
    public WorkflowResponse update(Long id, WorkflowUpdateRequest request) {
        WorkflowDefinitionRecord current = requireDefinitionForUpdate(id);
        if (current.getVersion() == null
                || !current.getVersion().equals(request.expectedVersion())) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.WORKFLOW_PUBLISH_CONFLICT,
                    "workflow 已被其他请求修改，请刷新后重试");
        }
        WorkflowGraphValidator.Graph graph = request.graph() == null
                ? null : toGraph(request.graph());
        if (graph != null) {
            validateGraph(graph, current.getWorkspaceId());
        }
        current.setName(requiredText(request.name(), "name", 128));
        current.setDescription(normalizeOptional(request.description(), 512));
        Instant now = Instant.now();
        current.setUpdatedAt(now);
        if (definitionMapper.updateMetadata(current, request.expectedVersion()) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.WORKFLOW_PUBLISH_CONFLICT,
                    "workflow 状态已变化，请刷新后重试");
        }
        if (graph != null) {
            WorkflowVersionRecord version = versionMapper.findByDefinitionId(id).stream()
                    .filter(candidate -> "DRAFT".equals(candidate.getStatus()))
                    .findFirst()
                    .orElse(null);
            if (version == null) {
                version = newVersion(
                        id, versionMapper.nextVersionNumber(id), request.metadata(), now);
                versionMapper.insert(version);
            } else {
                if (versionMapper.updateDraftMetadata(
                        version.getId(), id, writeJson(request.metadata()), now) != 1) {
                    throw new ApiException(HttpStatus.CONFLICT,
                            ErrorCode.WORKFLOW_VERSION_INVALID,
                            "workflow 草稿状态已变化，请刷新后重试");
                }
                edgeMapper.deleteByVersionId(version.getId());
                nodeMapper.deleteByVersionId(version.getId());
            }
            persistGraph(version.getId(), graph, now);
        }
        return get(id);
    }

    @Transactional
    public WorkflowVersionResponse createVersion(Long id, WorkflowVersionRequest request) {
        WorkflowDefinitionRecord definition = requireDefinitionForUpdate(id);
        requireActive(definition);
        WorkflowGraphValidator.Graph graph = toGraph(request.graph());
        validateGraph(graph, definition.getWorkspaceId());
        Instant now = Instant.now();
        WorkflowVersionRecord existingDraft = versionMapper.findByDefinitionId(id).stream()
                .filter(candidate -> "DRAFT".equals(candidate.getStatus()))
                .findFirst()
                .orElse(null);
        if (existingDraft != null) {
            if (versionMapper.updateDraftMetadata(
                    existingDraft.getId(), id, writeJson(request.metadata()), now) != 1) {
                throw new ApiException(HttpStatus.CONFLICT, ErrorCode.WORKFLOW_VERSION_INVALID,
                        "workflow 草稿状态已变化，请刷新后重试");
            }
            edgeMapper.deleteByVersionId(existingDraft.getId());
            nodeMapper.deleteByVersionId(existingDraft.getId());
            persistGraph(existingDraft.getId(), graph, now);
            return getVersion(id, existingDraft.getId());
        }
        int versionNumber = versionMapper.nextVersionNumber(id);
        WorkflowVersionRecord version = newVersion(
                id, versionNumber, request.metadata(), now);
        versionMapper.insert(version);
        persistGraph(version.getId(), graph, now);
        return getVersion(id, version.getId());
    }

    @Transactional
    public WorkflowVersionResponse updateVersion(Long id,
                                                 Long versionId,
                                                 WorkflowVersionRequest request) {
        WorkflowDefinitionRecord definition = requireDefinitionForUpdate(id);
        WorkflowVersionRecord version = requireVersionForUpdate(id, versionId);
        requireDraft(version);
        WorkflowGraphValidator.Graph graph = toGraph(request.graph());
        validateGraph(graph, definition.getWorkspaceId());
        Instant now = Instant.now();
        if (versionMapper.updateDraftMetadata(
                versionId, id, writeJson(request.metadata()), now) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.WORKFLOW_VERSION_INVALID,
                    "workflow 版本状态已变化，请刷新后重试");
        }
        edgeMapper.deleteByVersionId(versionId);
        nodeMapper.deleteByVersionId(versionId);
        persistGraph(versionId, graph, now);
        return getVersion(id, versionId);
    }

    @Transactional(readOnly = true)
    public WorkflowVersionResponse getVersion(Long id, Long versionId) {
        requireDefinition(id);
        WorkflowVersionRecord version = requireVersion(id, versionId);
        return toVersionResponse(version);
    }

    @Transactional(readOnly = true)
    public WorkflowValidationResponse validateVersion(Long id, Long versionId) {
        WorkflowDefinitionRecord definition = requireDefinition(id);
        WorkflowVersionRecord version = requireVersion(id, versionId);
        WorkflowGraphValidator.ValidationResult result =
                graphValidator.validate(toGraph(version));
        if (!result.valid()) {
            return new WorkflowValidationResponse(false, result.errors());
        }
        try {
            validateReferences(toGraph(version), definition.getWorkspaceId());
            return new WorkflowValidationResponse(true, List.of());
        } catch (ApiException exception) {
            return new WorkflowValidationResponse(false, List.of(exception.getMessage()));
        }
    }

    @Transactional(readOnly = true)
    public WorkflowValidationResponse validateDraft(Long id, WorkflowGraphRequest request) {
        WorkflowDefinitionRecord definition = requireDefinition(id);
        WorkflowGraphValidator.Graph graph = request == null
                ? toGraph(requireLatestDraft(id))
                : toGraph(request);
        WorkflowGraphValidator.ValidationResult result = graphValidator.validate(graph);
        if (!result.valid()) {
            return new WorkflowValidationResponse(false, result.errors());
        }
        try {
            validateReferences(graph, definition.getWorkspaceId());
            return new WorkflowValidationResponse(true, List.of());
        } catch (ApiException exception) {
            return new WorkflowValidationResponse(false, List.of(exception.getMessage()));
        }
    }

    @Transactional
    public WorkflowResponse publish(Long id, WorkflowCommandRequest request) {
        WorkflowDefinitionRecord definition = requireDefinitionForUpdate(id);
        requireActive(definition);
        if (request.expectedVersion() != null
                && !request.expectedVersion().equals(definition.getVersion())) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.WORKFLOW_PUBLISH_CONFLICT,
                    "workflow 已被其他请求修改，请刷新后重试");
        }
        WorkflowVersionRecord version = request.versionId() == null
                ? requireLatestDraft(id)
                : requireVersionForUpdate(id, request.versionId());
        requireDraft(version);
        validateGraph(toGraph(version), definition.getWorkspaceId());
        Instant now = Instant.now();
        if (versionMapper.publishDraft(version.getId(), id, now) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.WORKFLOW_PUBLISH_CONFLICT,
                    "workflow 版本状态已变化，请刷新后重试");
        }
        if (definitionMapper.setPublishedVersion(id, version.getId(), now) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.WORKFLOW_PUBLISH_CONFLICT,
                    "workflow 发布指针更新失败，请刷新后重试");
        }
        return get(id);
    }

    @Transactional
    public WorkflowResponse disable(Long id) {
        WorkflowDefinitionRecord definition = requireDefinitionForUpdate(id);
        Instant now = Instant.now();
        versionMapper.disableAll(id, now);
        if (definitionMapper.disable(id, now) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.WORKFLOW_PUBLISH_CONFLICT,
                    "workflow 状态已变化，请刷新后重试");
        }
        return get(id);
    }

    public WorkflowVersionRecord requirePublishedVersion(Long id, Long requestedVersionId) {
        WorkflowDefinitionRecord definition = requireDefinition(id);
        requireActive(definition);
        Long versionId = requestedVersionId == null
                ? definition.getPublishedVersionId() : requestedVersionId;
        if (versionId == null) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.WORKFLOW_VERSION_INVALID,
                    "workflow 尚未发布可运行版本");
        }
        WorkflowVersionRecord version = requireVersion(id, versionId);
        if (!"PUBLISHED".equals(version.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.WORKFLOW_VERSION_INVALID,
                    "workflow 运行只能绑定已发布版本");
        }
        validateGraph(toGraph(version), definition.getWorkspaceId());
        return version;
    }

    public WorkflowGraphValidator.Graph graph(WorkflowVersionRecord version) {
        return toGraph(version);
    }

    private void persistGraph(Long versionId,
                              WorkflowGraphValidator.Graph graph,
                              Instant now) {
        Map<String, WorkflowNodeRecord> nodes = new LinkedHashMap<>();
        for (WorkflowGraphValidator.Node source : graph.nodes()) {
            WorkflowNodeRecord node = new WorkflowNodeRecord();
            node.setWorkflowVersionId(versionId);
            node.setNodeKey(source.key());
            node.setNodeType(source.type());
            node.setName(source.name());
            node.setConfigJson(writeJson(source.config()));
            node.setPositionJson(source.position() == null
                    ? null : writeJson(source.position()));
            node.setCreatedAt(now);
            node.setUpdatedAt(now);
            nodeMapper.insert(node);
            nodes.put(source.key(), node);
        }
        for (WorkflowGraphValidator.Edge source : graph.edges()) {
            WorkflowEdgeRecord edge = new WorkflowEdgeRecord();
            edge.setWorkflowVersionId(versionId);
            edge.setSourceNodeId(nodes.get(source.from()).getId());
            edge.setTargetNodeId(nodes.get(source.to()).getId());
            edge.setBranchKey(source.branch());
            edge.setSortOrder(source.order() == null ? 0 : source.order());
            edge.setCreatedAt(now);
            edgeMapper.insert(edge);
        }
    }

    private WorkflowVersionRecord newVersion(Long definitionId,
                                             int versionNumber,
                                             Map<String, Object> metadata,
                                             Instant now) {
        WorkflowVersionRecord version = new WorkflowVersionRecord();
        version.setWorkflowDefinitionId(definitionId);
        version.setVersionNumber(versionNumber);
        version.setStatus("DRAFT");
        version.setMetadataJson(writeJson(metadata));
        version.setCreatedAt(now);
        version.setUpdatedAt(now);
        return version;
    }

    private WorkflowDefinitionRecord requireDefinition(Long id) {
        WorkflowDefinitionRecord definition = definitionMapper.findById(id);
        if (definition == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.WORKFLOW_NOT_FOUND,
                    "workflow 不存在");
        }
        return definition;
    }

    private WorkflowDefinitionRecord requireDefinitionForUpdate(Long id) {
        WorkflowDefinitionRecord definition = definitionMapper.findByIdForUpdate(id);
        if (definition == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.WORKFLOW_NOT_FOUND,
                    "workflow 不存在");
        }
        return definition;
    }

    private WorkflowVersionRecord requireVersion(Long definitionId, Long versionId) {
        WorkflowVersionRecord version = versionMapper.findById(versionId);
        if (version == null || !definitionId.equals(version.getWorkflowDefinitionId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.WORKFLOW_VERSION_INVALID,
                    "workflow 版本不存在");
        }
        return version;
    }

    private WorkflowVersionRecord requireVersionForUpdate(Long definitionId, Long versionId) {
        WorkflowVersionRecord version = versionMapper.findByIdForUpdate(versionId);
        if (version == null || !definitionId.equals(version.getWorkflowDefinitionId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.WORKFLOW_VERSION_INVALID,
                    "workflow 版本不存在");
        }
        return version;
    }

    private WorkflowVersionRecord requireLatestDraft(Long definitionId) {
        return versionMapper.findByDefinitionId(definitionId).stream()
                .filter(version -> "DRAFT".equals(version.getStatus()))
                .findFirst()
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT, ErrorCode.WORKFLOW_VERSION_INVALID,
                        "workflow 没有可发布的草稿版本"));
    }

    private void requireWorkspace(Long workspaceId) {
        if (workspaceMapper.findById(workspaceId) == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "工作空间不存在");
        }
    }

    private void requireActive(WorkflowDefinitionRecord definition) {
        if (!"ACTIVE".equals(definition.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.WORKFLOW_PUBLISH_CONFLICT,
                    "workflow 已停用");
        }
    }

    private void requireDraft(WorkflowVersionRecord version) {
        if (!"DRAFT".equals(version.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.WORKFLOW_VERSION_INVALID,
                    "已发布或已停用版本不可修改");
        }
    }

    private void validateGraph(WorkflowGraphValidator.Graph graph, Long workspaceId) {
        WorkflowGraphValidator.ValidationResult result = graphValidator.validate(graph);
        validateGraph(result);
        validateReferences(graph, workspaceId);
    }

    private void validateGraph(WorkflowGraphValidator.ValidationResult result) {
        if (!result.valid()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.WORKFLOW_GRAPH_INVALID,
                    String.join("；", result.errors()));
        }
    }

    private void validateReferences(WorkflowGraphValidator.Graph graph, Long workspaceId) {
        for (WorkflowGraphValidator.Node node : graph.nodes()) {
            Map<String, Object> config = node.config() == null ? Map.of() : node.config();
            if ("AGENT".equals(node.type())) {
                Long agentVersionId = number(config.get("agentVersionId"));
                AgentVersionRecord version = agentVersionMapper.findById(agentVersionId);
                if (version == null || !"PUBLISHED".equals(version.getStatus())) {
                    throw new ApiException(HttpStatus.BAD_REQUEST,
                            ErrorCode.WORKFLOW_GRAPH_INVALID,
                            "AGENT 节点必须绑定已发布 AgentVersion: " + node.key());
                }
                AgentDefinitionDetailRecord agent = agentDefinitionMapper
                        .findAgent(version.getAgentDefinitionId());
                if (agent == null || !workspaceId.equals(agent.getWorkspaceId())) {
                    throw new ApiException(HttpStatus.BAD_REQUEST,
                            ErrorCode.WORKFLOW_GRAPH_INVALID,
                            "AGENT 节点引用的 Agent 不属于当前工作空间: " + node.key());
                }
            }
            if ("TOOL".equals(node.type())) {
                String toolCode = (String) config.get("toolCode");
                Integer toolVersion = intValue(config.get("toolVersion"));
                ToolVersionRecord version = toolVersionMapper
                        .findPublishedByCodeAndVersion(toolCode, toolVersion);
                if (version == null) {
                    throw new ApiException(HttpStatus.BAD_REQUEST,
                            ErrorCode.WORKFLOW_GRAPH_INVALID,
                            "TOOL 节点绑定的工具不存在或未启用: " + node.key());
                }
                validateMcpToolReference(toolCode, workspaceId, node.key());
            }
        }
    }

    private void validateMcpToolReference(String toolCode,
                                          Long workspaceId,
                                          String nodeKey) {
        McpToolRecord mcpTool = mcpToolMapper.findByToolCode(toolCode);
        if (mcpTool == null) {
            return;
        }
        McpServerRecord server = mcpServerMapper.findById(mcpTool.getMcpServerId());
        if (server == null || !workspaceId.equals(server.getWorkspaceId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    ErrorCode.WORKFLOW_GRAPH_INVALID,
                    "TOOL 节点引用的 MCP Tool 不属于当前工作空间: " + nodeKey);
        }
        if (!mcpTool.isEnabled()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    ErrorCode.WORKFLOW_GRAPH_INVALID,
                    "TOOL 节点引用的 MCP Tool 尚未启用: " + nodeKey);
        }
    }

    private WorkflowGraphValidator.Graph toGraph(WorkflowGraphRequest request) {
        List<WorkflowGraphValidator.Node> nodes = request.nodes().stream()
                .map(node -> new WorkflowGraphValidator.Node(
                        requiredText(node.key(), "node.key", 64),
                        upper(node.type()),
                        requiredText(node.name(), "node.name", 128),
                        node.config() == null ? Map.of() : node.config(),
                        node.position()))
                .toList();
        List<WorkflowGraphValidator.Edge> edges = request.edges().stream()
                .map(edge -> new WorkflowGraphValidator.Edge(
                        requiredText(edge.from(), "edge.from", 64),
                        requiredText(edge.to(), "edge.to", 64),
                        normalizeBranch(edge.branch()),
                        edge.order() == null ? 0 : edge.order()))
                .toList();
        return new WorkflowGraphValidator.Graph(nodes, edges);
    }

    private WorkflowGraphValidator.Graph toGraph(WorkflowVersionRecord version) {
        List<WorkflowNodeRecord> nodes = nodeMapper.findByVersionId(version.getId());
        List<WorkflowEdgeRecord> edges = edgeMapper.findByVersionId(version.getId());
        List<WorkflowGraphValidator.Node> graphNodes = nodes.stream()
                .map(node -> new WorkflowGraphValidator.Node(
                        node.getNodeKey(),
                        node.getNodeType(),
                        node.getName(),
                        readMap(node.getConfigJson()),
                        readMap(node.getPositionJson())))
                .toList();
        List<WorkflowGraphValidator.Edge> graphEdges = edges.stream()
                .map(edge -> new WorkflowGraphValidator.Edge(
                        edge.getSourceNodeKey(),
                        edge.getTargetNodeKey(),
                        edge.getBranchKey(),
                        edge.getSortOrder()))
                .toList();
        return new WorkflowGraphValidator.Graph(graphNodes, graphEdges);
    }

    private WorkflowResponse toResponse(WorkflowDefinitionRecord definition) {
        List<WorkflowVersionSummaryResponse> versions = versionMapper
                .findByDefinitionId(definition.getId()).stream()
                .map(version -> new WorkflowVersionSummaryResponse(
                        version.getId(),
                        version.getWorkflowDefinitionId(),
                        version.getVersionNumber(),
                        version.getStatus(),
                        version.getPublishedAt(),
                        version.getCreatedAt(),
                        version.getUpdatedAt()))
                .toList();
        return new WorkflowResponse(
                definition.getId(),
                definition.getWorkspaceId(),
                definition.getCode(),
                definition.getName(),
                definition.getDescription(),
                definition.getStatus(),
                definition.getPublishedVersionId(),
                definition.getVersion(),
                versions,
                definition.getCreatedAt(),
                definition.getUpdatedAt());
    }

    private WorkflowVersionResponse toVersionResponse(WorkflowVersionRecord version) {
        List<WorkflowNodeResponse> nodes = nodeMapper.findByVersionId(version.getId()).stream()
                .map(node -> new WorkflowNodeResponse(
                        node.getId(),
                        node.getNodeKey(),
                        node.getNodeType(),
                        node.getName(),
                        readMapOrObject(node.getConfigJson()),
                        readMapOrObject(node.getPositionJson())))
                .toList();
        List<WorkflowEdgeResponse> edges = edgeMapper.findByVersionId(version.getId()).stream()
                .map(edge -> new WorkflowEdgeResponse(
                        edge.getId(),
                        edge.getSourceNodeKey(),
                        edge.getTargetNodeKey(),
                        edge.getBranchKey(),
                        edge.getSortOrder()))
                .toList();
        return new WorkflowVersionResponse(
                version.getId(),
                version.getWorkflowDefinitionId(),
                version.getVersionNumber(),
                version.getStatus(),
                readMapOrObject(version.getMetadataJson()),
                nodes,
                edges,
                version.getPublishedAt(),
                version.getCreatedAt(),
                version.getUpdatedAt());
    }

    private Map<String, Object> readMap(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, objectMapper.getTypeFactory()
                    .constructMapType(LinkedHashMap.class, String.class, Object.class));
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    private Object readMapOrObject(String value) {
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
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.WORKFLOW_GRAPH_INVALID,
                    "workflow 配置无法序列化");
        }
    }

    private String requiredText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    field + " 不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    field + " 长度不能超过 " + maxLength);
        }
        return normalized;
    }

    private String normalizeOptional(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return requiredText(value, "description", maxLength);
    }

    private String normalizeListStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        if (!List.of("DRAFT", "PUBLISHED", "DISABLED").contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "status 必须为 DRAFT、PUBLISHED 或 DISABLED");
        }
        return normalized;
    }

    private String upper(String value) {
        return requiredText(value, "node.type", 16).toUpperCase();
    }

    private String normalizeBranch(String value) {
        return value == null || value.isBlank() ? "DEFAULT" : value.trim().toUpperCase();
    }

    private Long number(Object value) {
        if (value instanceof Number number
                && number.longValue() > 0
                && number.doubleValue() == number.longValue()) {
            return number.longValue();
        }
        return null;
    }

    private Integer intValue(Object value) {
        Long number = number(value);
        return number == null || number > Integer.MAX_VALUE ? null : number.intValue();
    }
}
