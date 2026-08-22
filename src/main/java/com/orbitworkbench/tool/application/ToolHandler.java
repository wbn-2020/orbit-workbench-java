package com.orbitworkbench.tool.application;

import com.fasterxml.jackson.databind.JsonNode;

public interface ToolHandler {

    String toolCode();

    ToolExecutionResult execute(ToolExecutionContext context, JsonNode arguments);
}
