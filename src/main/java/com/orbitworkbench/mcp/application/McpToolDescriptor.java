package com.orbitworkbench.mcp.application;

import com.fasterxml.jackson.databind.JsonNode;

public record McpToolDescriptor(
        String name,
        String title,
        String description,
        JsonNode inputSchema,
        JsonNode outputSchema
) {
}
