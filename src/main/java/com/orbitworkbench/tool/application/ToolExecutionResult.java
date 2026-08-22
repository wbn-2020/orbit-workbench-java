package com.orbitworkbench.tool.application;

import com.fasterxml.jackson.databind.JsonNode;

public record ToolExecutionResult(
        JsonNode content,
        String summary
) {
    public ToolExecutionResult {
        if (content == null) {
            throw new IllegalArgumentException("工具结果不能为空");
        }
    }
}
