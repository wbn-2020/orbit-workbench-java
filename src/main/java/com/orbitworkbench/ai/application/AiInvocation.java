package com.orbitworkbench.ai.application;

import com.orbitworkbench.aiconnection.application.AiConnectionService.AiConnectionRuntimeConfig;

public record AiInvocation(
        AiConnectionRuntimeConfig connection,
        String systemPrompt,
        String userPrompt,
        Long runId,
        Long modelCallId,
        boolean stream,
        int maxOutputTokens
) {
    public AiInvocation {
        if (maxOutputTokens <= 0) {
            throw new IllegalArgumentException("maxOutputTokens 必须大于 0");
        }
    }
}
