package com.orbitworkbench.mcp.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.orbitworkbench.mcp.domain.McpServerRecord;
import com.orbitworkbench.mcp.domain.McpToolRecord;
import com.orbitworkbench.mcp.infrastructure.mapper.McpServerMapper;
import com.orbitworkbench.mcp.infrastructure.mapper.McpToolMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.shared.config.McpProperties;
import com.orbitworkbench.tool.application.ToolExecutionContext;
import com.orbitworkbench.tool.application.ToolExecutionResult;
import com.orbitworkbench.tool.application.ToolHandler;
import com.orbitworkbench.tool.domain.ToolVersionRecord;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class McpToolRuntime {

    private final McpServerMapper serverMapper;
    private final McpToolMapper toolMapper;
    private final McpClient client;
    private final McpProperties properties;

    public McpToolRuntime(McpServerMapper serverMapper,
                          McpToolMapper toolMapper,
                          McpClient client,
                          McpProperties properties) {
        this.serverMapper = serverMapper;
        this.toolMapper = toolMapper;
        this.client = client;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public boolean hasHandler(String toolCode) {
        return toolCode != null && toolMapper.findByToolCode(toolCode) != null;
    }

    public ToolHandler handlerFor(ToolVersionRecord version) {
        return new ToolHandler() {
            @Override
            public String toolCode() {
                return version.getToolCode();
            }

            @Override
            public ToolExecutionResult execute(ToolExecutionContext context,
                                               JsonNode arguments) {
                return executeMcp(version, arguments);
            }
        };
    }

    private ToolExecutionResult executeMcp(ToolVersionRecord version,
                                           JsonNode arguments) {
        McpToolRecord tool = toolMapper.findByToolCode(version.getToolCode());
        if (tool == null
                || !tool.isEnabled()) {
            throw new ApiException(HttpStatus.CONFLICT,
                    ErrorCode.MCP_TOOL_DISABLED,
                    "MCP Tool 未启用或版本已变化");
        }
        McpServerRecord server = serverMapper.findById(tool.getMcpServerId());
        if (server == null || !"ENABLED".equals(server.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    ErrorCode.MCP_TOOL_DISABLED,
                    "MCP Server 未启用");
        }
        Duration timeout = version.getTimeoutMs() == null
                ? properties.getRequestTimeout()
                : Duration.ofMillis(version.getTimeoutMs());
        int maxBytes = version.getMaxResultBytes() == null
                ? properties.getMaxResponseBytes()
                : Math.min(version.getMaxResultBytes(), properties.getMaxResponseBytes());
        JsonNode result = client.callTool(
                server, tool.getToolName(), arguments, timeout, maxBytes);
        return new ToolExecutionResult(result, "MCP Tool 调用完成");
    }
}
