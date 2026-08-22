package com.orbitworkbench.tool.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.orbitworkbench.tool.domain.ToolCallRecord;

public record ToolExecutionOutcome(
        ToolCallRecord toolCall,
        JsonNode result
) {
}
