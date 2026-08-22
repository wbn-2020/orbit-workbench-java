package com.orbitworkbench.ai.application;

import com.fasterxml.jackson.databind.JsonNode;

public record AiToolDefinition(
        String name,
        String description,
        JsonNode parameters
) {
}
