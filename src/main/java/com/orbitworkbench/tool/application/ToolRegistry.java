package com.orbitworkbench.tool.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.ai.application.AiToolDefinition;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.tool.domain.ToolVersionRecord;
import com.orbitworkbench.tool.infrastructure.mapper.ToolVersionMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ToolRegistry {

    private final ToolVersionMapper versionMapper;
    private final ObjectMapper objectMapper;
    private final Map<String, ToolHandler> handlers;

    public ToolRegistry(ToolVersionMapper versionMapper,
                        ObjectMapper objectMapper,
                        List<ToolHandler> handlerList) {
        this.versionMapper = versionMapper;
        this.objectMapper = objectMapper;
        Map<String, ToolHandler> registered = new LinkedHashMap<>();
        for (ToolHandler handler : handlerList) {
            ToolHandler previous = registered.put(handler.toolCode(), handler);
            if (previous != null) {
                throw new IllegalStateException(
                        "重复的 ToolHandler: " + handler.toolCode());
            }
        }
        this.handlers = Map.copyOf(registered);
    }

    @Transactional(readOnly = true)
    public RegisteredTool require(String toolCode) {
        ToolVersionRecord version = versionMapper.findPublishedLatestByCode(toolCode);
        return requireRegistered(toolCode, version);
    }

    @Transactional(readOnly = true)
    public RegisteredTool require(String toolCode, Long toolVersionId) {
        if (toolVersionId == null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.TOOL_DISABLED,
                    "当前 Agent 未授权该工具版本");
        }
        ToolVersionRecord version = versionMapper.findById(toolVersionId);
        if (version == null
                || !Objects.equals(toolCode, version.getToolCode())
                || !"PUBLISHED".equals(version.getStatus())
                || !"PUBLISHED".equals(version.getCatalogStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.TOOL_DISABLED,
                    "工具版本不存在、未发布或已停用");
        }
        return requireRegistered(toolCode, version);
    }

    public boolean hasHandler(String toolCode) {
        return toolCode != null && handlers.containsKey(toolCode);
    }

    private RegisteredTool requireRegistered(String toolCode, ToolVersionRecord version) {
        if (version == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    ErrorCode.TOOL_NOT_FOUND,
                    "工具不存在或未启用");
        }
        ToolHandler handler = handlers.get(toolCode);
        if (handler == null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.TOOL_DISABLED,
                    "工具处理器未注册");
        }
        return new RegisteredTool(version, handler);
    }

    @Transactional(readOnly = true)
    public List<AiToolDefinition> modelDefinitions() {
        return handlers.keySet().stream()
                .sorted()
                .map(versionMapper::findPublishedLatestByCode)
                .filter(version -> version != null)
                .map(version -> new AiToolDefinition(
                        version.getToolCode(),
                        version.getDescription(),
                        readSchema(version.getInputSchemaJson())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AiToolDefinition> modelDefinitions(Map<String, Long> toolVersionIds) {
        return toolVersionIds.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> require(entry.getKey(), entry.getValue()).version())
                .map(version -> new AiToolDefinition(
                        version.getToolCode(),
                        version.getDescription(),
                        readSchema(version.getInputSchemaJson())))
                .toList();
    }

    private com.fasterxml.jackson.databind.JsonNode readSchema(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    ErrorCode.TOOL_DISABLED,
                    "工具参数 Schema 无法读取");
        }
    }

    public record RegisteredTool(
            ToolVersionRecord version,
            ToolHandler handler
    ) {
    }
}
