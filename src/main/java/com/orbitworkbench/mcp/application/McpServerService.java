package com.orbitworkbench.mcp.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.mcp.api.McpDtos.McpServerRequest;
import com.orbitworkbench.mcp.api.McpDtos.McpServerResponse;
import com.orbitworkbench.mcp.api.McpDtos.McpToolResponse;
import com.orbitworkbench.mcp.domain.McpServerRecord;
import com.orbitworkbench.mcp.domain.McpToolRecord;
import com.orbitworkbench.mcp.infrastructure.mapper.McpServerMapper;
import com.orbitworkbench.mcp.infrastructure.mapper.McpToolMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.shared.config.McpProperties;
import com.orbitworkbench.tool.domain.ToolCatalogRecord;
import com.orbitworkbench.tool.domain.ToolVersionRecord;
import com.orbitworkbench.tool.application.ToolCatalogService;
import com.orbitworkbench.tool.infrastructure.mapper.ToolCatalogMapper;
import com.orbitworkbench.tool.infrastructure.mapper.ToolVersionMapper;
import com.orbitworkbench.workspace.application.WorkspaceService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class McpServerService {

    private final McpServerMapper serverMapper;
    private final McpToolMapper toolMapper;
    private final ToolCatalogMapper catalogMapper;
    private final ToolVersionMapper versionMapper;
    private final ToolCatalogService toolCatalogService;
    private final WorkspaceService workspaceService;
    private final McpClient client;
    private final McpEndpointPolicy endpointPolicy;
    private final ObjectMapper objectMapper;
    private final McpProperties properties;
    private final TransactionTemplate transactionTemplate;

    public McpServerService(McpServerMapper serverMapper,
                            McpToolMapper toolMapper,
                            ToolCatalogMapper catalogMapper,
                            ToolVersionMapper versionMapper,
                            ToolCatalogService toolCatalogService,
                            WorkspaceService workspaceService,
                            McpClient client,
                            McpEndpointPolicy endpointPolicy,
                            ObjectMapper objectMapper,
                            McpProperties properties,
                            TransactionTemplate transactionTemplate) {
        this.serverMapper = serverMapper;
        this.toolMapper = toolMapper;
        this.catalogMapper = catalogMapper;
        this.versionMapper = versionMapper;
        this.toolCatalogService = toolCatalogService;
        this.workspaceService = workspaceService;
        this.client = client;
        this.endpointPolicy = endpointPolicy;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
    }

    @Transactional(readOnly = true)
    public List<McpServerResponse> list(Long workspaceId) {
        workspaceService.require(workspaceId);
        return serverMapper.findByWorkspaceId(workspaceId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public McpServerResponse get(Long id) {
        return toResponse(require(id));
    }

    @Transactional
    public McpServerResponse create(McpServerRequest request) {
        workspaceService.require(request.workspaceId());
        String code = requiredText(request.serverCode(), "serverCode", 64);
        String name = requiredText(request.name(), "name", 128);
        String transport = normalizeTransport(request.transport());
        String endpointUrl = normalizeEndpoint(request.endpointUrl());
        endpointPolicy.validateSyntax(endpointUrl);
        Instant now = Instant.now();
        McpServerRecord server = new McpServerRecord();
        server.setWorkspaceId(request.workspaceId());
        server.setServerCode(code);
        server.setName(name);
        server.setTransport(transport);
        server.setEndpointUrl(endpointUrl);
        server.setCredentialRef(normalizeCredentialRef(request.credentialRef()));
        server.setAllowPrivateNetwork(request.allowPrivateNetwork());
        server.setStatus("DISABLED");
        server.setSyncStatus("NEVER");
        server.setLockVersion(1L);
        server.setCreatedAt(now);
        server.setUpdatedAt(now);
        try {
            serverMapper.insert(server);
        } catch (DuplicateKeyException exception) {
            throw invalid("当前工作空间已经存在相同 MCP Server code");
        }
        return get(server.getId());
    }

    @Transactional
    public McpServerResponse update(Long id, McpServerRequest request) {
        McpServerRecord current = require(id);
        if (!Objects.equals(current.getWorkspaceId(), request.workspaceId())) {
            throw invalid("MCP Server 不属于请求的工作空间");
        }
        long expectedVersion = request.expectedVersion() == null
                ? current.getLockVersion() : request.expectedVersion();
        if (!Objects.equals(current.getLockVersion(), expectedVersion)) {
            throw conflict("MCP Server 已被其他请求修改，请刷新后重试");
        }
        String endpointUrl = normalizeEndpoint(request.endpointUrl());
        endpointPolicy.validateSyntax(endpointUrl);
        current.setServerCode(requiredText(request.serverCode(), "serverCode", 64));
        current.setName(requiredText(request.name(), "name", 128));
        current.setTransport(normalizeTransport(request.transport()));
        current.setEndpointUrl(endpointUrl);
        if (request.credentialRef() != null) {
            current.setCredentialRef(normalizeCredentialRef(request.credentialRef()));
        }
        current.setAllowPrivateNetwork(request.allowPrivateNetwork());
        current.setUpdatedAt(Instant.now());
        if (serverMapper.update(current, expectedVersion) != 1) {
            throw conflict("MCP Server 状态已变化，请刷新后重试");
        }
        return get(id);
    }

    @Transactional
    public McpServerResponse enable(Long id) {
        McpServerRecord server = require(id);
        if (!"SUCCESS".equals(server.getSyncStatus())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    ErrorCode.MCP_SERVER_INVALID,
                    "MCP Server 必须先同步成功后才能启用");
        }
        if (!"ENABLED".equals(server.getStatus())
                && serverMapper.updateStatus(
                        id, server.getStatus(), "ENABLED", Instant.now()) != 1) {
            throw conflict("MCP Server 状态已变化，请刷新后重试");
        }
        return get(id);
    }

    @Transactional
    public McpServerResponse disable(Long id) {
        McpServerRecord server = require(id);
        if (!"DISABLED".equals(server.getStatus())
                && serverMapper.updateStatus(
                        id, server.getStatus(), "DISABLED", Instant.now()) != 1) {
            throw conflict("MCP Server 状态已变化，请刷新后重试");
        }
        return get(id);
    }

    public McpServerResponse sync(Long id) {
        McpServerRecord server = require(id);
        try {
            List<McpToolDescriptor> descriptors = client.listTools(server);
            transactionTemplate.executeWithoutResult(status -> persistSync(server, descriptors));
            return get(id);
        } catch (ApiException exception) {
            markSyncFailure(id, exception.getErrorCode(), safeSummary(exception.getMessage()));
            throw exception;
        } catch (RuntimeException exception) {
            markSyncFailure(id, ErrorCode.MCP_SERVER_UNAVAILABLE,
                    "MCP Server 同步失败");
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    ErrorCode.MCP_SERVER_UNAVAILABLE,
                    "MCP Server 同步失败");
        }
    }

    @Transactional(readOnly = true)
    public List<McpToolResponse> tools(Long serverId) {
        require(serverId);
        return toolMapper.findByServerId(serverId).stream()
                .map(this::toToolResponse)
                .toList();
    }

    @Transactional
    public McpToolResponse enableTool(Long toolId) {
        McpToolRecord tool = requireTool(toolId);
        McpServerRecord server = require(tool.getMcpServerId());
        if (!"ENABLED".equals(server.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    ErrorCode.MCP_TOOL_DISABLED,
                    "MCP Server 未启用");
        }
        toolCatalogService.enable(tool.getToolCatalogId());
        if (toolMapper.updateEnabled(toolId, true, Instant.now()) != 1) {
            throw conflict("MCP Tool 状态已变化，请刷新后重试");
        }
        return toToolResponse(toolMapper.findById(toolId));
    }

    @Transactional
    public McpToolResponse disableTool(Long toolId) {
        McpToolRecord tool = requireTool(toolId);
        toolCatalogService.disable(tool.getToolCatalogId());
        if (toolMapper.updateEnabled(toolId, false, Instant.now()) != 1) {
            throw conflict("MCP Tool 状态已变化，请刷新后重试");
        }
        return toToolResponse(toolMapper.findById(toolId));
    }

    private void persistSync(McpServerRecord requested,
                             List<McpToolDescriptor> descriptors) {
        McpServerRecord server = serverMapper.findByIdForUpdate(requested.getId());
        if (server == null) {
            throw new ApiException(HttpStatus.NOT_FOUND,
                    ErrorCode.MCP_SERVER_INVALID,
                    "MCP Server 不存在");
        }
        List<String> toolNames = descriptors.stream()
                .map(McpToolDescriptor::name)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(name -> !name.isBlank())
                .toList();
        for (McpToolDescriptor descriptor : descriptors) {
            upsertTool(server, descriptor);
        }
        toolMapper.disableExceptNames(server.getId(), toolNames, Instant.now());
        if (serverMapper.markSyncSuccess(server.getId(), Instant.now()) != 1) {
            throw conflict("MCP Server 同步状态已变化");
        }
    }

    private void upsertTool(McpServerRecord server,
                            McpToolDescriptor descriptor) {
        String toolName = requiredText(descriptor.name(), "MCP tool name", 256);
        String toolCode = McpToolCodes.code(server.getId(), toolName);
        Instant now = Instant.now();
        McpToolRecord existing = toolMapper.findByServerAndName(
                server.getId(), toolName);
        ToolCatalogRecord catalog = catalogMapper.findByToolCode(toolCode);
        if (catalog == null) {
            catalog = new ToolCatalogRecord();
            catalog.setToolCode(toolCode);
            catalog.setName(firstText(descriptor.title(), toolName, 128));
            catalog.setDescription(optionalText(descriptor.description(), 512));
            catalog.setHandlerType("MCP");
            catalog.setStatus("DISABLED");
            catalog.setLockVersion(1L);
            catalog.setCreatedAt(now);
            catalog.setUpdatedAt(now);
            catalogMapper.insert(catalog);
        }
        ToolVersionRecord current = existing == null || existing.getToolVersionId() == null
                ? null : versionMapper.findById(existing.getToolVersionId());
        ToolVersionRecord version = sameMetadata(current, descriptor)
                ? current : createPublishedVersion(
                        catalog, server.getId(), descriptor, now);
        if (!Objects.equals(catalog.getPublishedVersionId(), version.getId())) {
            if (catalogMapper.setPublishedVersionPointer(
                    catalog.getId(), version.getId(), catalog.getLockVersion(), now) != 1) {
                throw conflict("MCP Tool 发布指针已变化，请重新同步");
            }
            catalog.setPublishedVersionId(version.getId());
            catalog.setLockVersion(catalog.getLockVersion() + 1);
        }
        catalogMapper.updateMetadata(
                catalog.getId(),
                firstText(descriptor.title(), toolName, 128),
                optionalText(descriptor.description(), 512),
                now);

        McpToolRecord tool = existing == null ? new McpToolRecord() : existing;
        tool.setMcpServerId(server.getId());
        tool.setToolCode(toolCode);
        tool.setToolName(toolName);
        tool.setTitle(firstText(descriptor.title(), toolName, 256));
        tool.setDescription(optionalText(descriptor.description(), 1024));
        tool.setToolCatalogId(catalog.getId());
        tool.setToolVersionId(version.getId());
        tool.setVersionNumber(version.getVersionNumber());
        tool.setCreatedAt(existing == null ? now : existing.getCreatedAt());
        tool.setUpdatedAt(now);
        if (existing == null) {
            toolMapper.insert(tool);
        } else if (toolMapper.update(tool) != 1) {
            throw conflict("MCP Tool 状态已变化，请重新同步");
        }
    }

    private ToolVersionRecord createPublishedVersion(ToolCatalogRecord catalog,
                                                     Long serverId,
                                                     McpToolDescriptor descriptor,
                                                     Instant now) {
        ToolVersionRecord version = new ToolVersionRecord();
        version.setToolCatalogId(catalog.getId());
        version.setVersionNumber(versionMapper.nextVersionNumber(catalog.getId()));
        version.setStatus("PUBLISHED");
        version.setInputSchemaJson(writeJson(descriptor.inputSchema()));
        version.setOutputSchemaJson(writeJson(descriptor.outputSchema()));
        version.setRiskLevel("MEDIUM");
        version.setRequiresConfirmation(true);
        version.setTimeoutMs(maxTimeoutMillis());
        version.setMaxResultBytes(maxResultBytes());
        version.setCapabilitiesJson(writeJson(objectMapper.valueToTree(Map.of(
                "source", "MCP",
                "serverId", serverId))));
        version.setPublishedAt(now);
        version.setCreatedAt(now);
        version.setUpdatedAt(now);
        versionMapper.insert(version);
        return version;
    }

    private boolean sameMetadata(ToolVersionRecord current,
                                 McpToolDescriptor descriptor) {
        if (current == null) {
            return false;
        }
        return readJson(current.getInputSchemaJson()).equals(
                descriptor.inputSchema())
                && readJson(current.getOutputSchemaJson()).equals(
                descriptor.outputSchema());
    }

    private McpServerRecord require(Long id) {
        McpServerRecord server = serverMapper.findById(id);
        if (server == null) {
            throw new ApiException(HttpStatus.NOT_FOUND,
                    ErrorCode.MCP_SERVER_INVALID,
                    "MCP Server 不存在");
        }
        return server;
    }

    private McpToolRecord requireTool(Long id) {
        McpToolRecord tool = toolMapper.findById(id);
        if (tool == null) {
            throw new ApiException(HttpStatus.NOT_FOUND,
                    ErrorCode.MCP_TOOL_DISABLED,
                    "MCP Tool 不存在");
        }
        return tool;
    }

    private McpServerResponse toResponse(McpServerRecord server) {
        return new McpServerResponse(
                server.getId(),
                server.getWorkspaceId(),
                server.getServerCode(),
                server.getName(),
                server.getTransport(),
                server.getEndpointUrl(),
                server.getCredentialRef() != null
                        && !server.getCredentialRef().isBlank(),
                server.isAllowPrivateNetwork(),
                server.getStatus(),
                server.getSyncStatus(),
                server.getLastSyncAt(),
                server.getLastErrorCode(),
                server.getLastErrorSummary(),
                server.getLockVersion(),
                tools(server.getId()),
                server.getCreatedAt(),
                server.getUpdatedAt());
    }

    private McpToolResponse toToolResponse(McpToolRecord tool) {
        ToolVersionRecord version = versionMapper.findById(tool.getToolVersionId());
        return new McpToolResponse(
                tool.getId(),
                tool.getMcpServerId(),
                tool.getToolCode(),
                tool.getToolName(),
                tool.getTitle(),
                tool.getDescription(),
                tool.getToolCatalogId(),
                tool.getToolVersionId(),
                tool.getVersionNumber(),
                tool.isEnabled(),
                version == null ? null : version.getCatalogStatus(),
                readJson(version == null ? null : version.getInputSchemaJson()),
                readJson(version == null ? null : version.getOutputSchemaJson()),
                version == null ? null : version.getRiskLevel(),
                version != null && version.isRequiresConfirmation(),
                version == null ? null : version.getTimeoutMs(),
                version == null ? null : version.getMaxResultBytes(),
                tool.getUpdatedAt());
    }

    private void markSyncFailure(Long id, ErrorCode errorCode, String summary) {
        transactionTemplate.executeWithoutResult(status ->
                serverMapper.markSyncFailure(
                        id, errorCode.name(), summary, Instant.now()));
    }

    private String writeJson(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(
                    value == null || value.isNull() ? objectMapper.createObjectNode() : value);
        } catch (JsonProcessingException exception) {
            throw invalid("MCP Tool Schema 无法保存");
        }
    }

    private JsonNode readJson(String value) {
        try {
            return value == null || value.isBlank()
                    ? objectMapper.createObjectNode()
                    : objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            return objectMapper.createObjectNode();
        }
    }

    private int maxTimeoutMillis() {
        return (int) Math.min(120_000L,
                Math.max(1L, normalizedDuration(properties.getRequestTimeout()).toMillis()));
    }

    private int maxResultBytes() {
        return Math.min(1_048_576, Math.max(1_024, properties.getMaxResponseBytes()));
    }

    private java.time.Duration normalizedDuration(java.time.Duration value) {
        return value == null || value.isZero() || value.isNegative()
                ? java.time.Duration.ofSeconds(30) : value;
    }

    private String normalizeTransport(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        if (!"STREAMABLE_HTTP".equals(normalized)) {
            throw invalid("当前仅支持 STREAMABLE_HTTP MCP Server");
        }
        return normalized;
    }

    private String normalizeEndpoint(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeCredentialRef(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String requiredText(String value, String field, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank() || normalized.length() > maxLength) {
            throw invalid(field + " 不合法");
        }
        return normalized;
    }

    private String optionalText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength
                ? normalized : normalized.substring(0, maxLength);
    }

    private String firstText(String value, String fallback, int maxLength) {
        return optionalText(value, maxLength) == null
                ? requiredText(fallback, "name", maxLength)
                : optionalText(value, maxLength);
    }

    private String safeSummary(String value) {
        if (value == null || value.isBlank()) {
            return "MCP Server 操作失败";
        }
        String normalized = value.replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .trim();
        return normalized.length() <= 512
                ? normalized : normalized.substring(0, 512);
    }

    private ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST,
                ErrorCode.MCP_SERVER_INVALID, message);
    }

    private ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT,
                ErrorCode.MCP_SERVER_INVALID, message);
    }
}
