package com.orbitworkbench.ai.application;

public record AiToolCall(
        String id,
        String name,
        String argumentsJson
) {
}
