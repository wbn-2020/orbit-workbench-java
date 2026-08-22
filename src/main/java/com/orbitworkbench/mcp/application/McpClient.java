package com.orbitworkbench.mcp.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.orbitworkbench.mcp.domain.McpServerRecord;
import java.time.Duration;
import java.util.List;

public interface McpClient {

    List<McpToolDescriptor> listTools(McpServerRecord server);

    JsonNode callTool(McpServerRecord server,
                      String toolName,
                      JsonNode arguments,
                      Duration timeout,
                      int maxResponseBytes);
}
