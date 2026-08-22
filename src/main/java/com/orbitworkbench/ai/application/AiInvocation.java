package com.orbitworkbench.ai.application;

import com.orbitworkbench.aiconnection.application.AiConnectionService.AiConnectionRuntimeConfig;
import java.util.List;

public record AiInvocation(
        AiConnectionRuntimeConfig connection,
        String systemPrompt,
        String userPrompt,
        Long runId,
        Long modelCallId,
        boolean stream,
        int maxOutputTokens,
        List<AiToolDefinition> tools,
        List<AiConversationItem> conversation,
        String previousResponseId
) {
    public AiInvocation {
        if (maxOutputTokens <= 0) {
            throw new IllegalArgumentException("maxOutputTokens 必须大于 0");
        }
        tools = tools == null ? List.of() : List.copyOf(tools);
        conversation = conversation == null ? List.of() : List.copyOf(conversation);
    }

    public AiInvocation(AiConnectionRuntimeConfig connection,
                        String systemPrompt,
                        String userPrompt,
                        Long runId,
                        Long modelCallId,
                        boolean stream,
                        int maxOutputTokens) {
        this(connection, systemPrompt, userPrompt, runId, modelCallId, stream,
                maxOutputTokens, List.of(), List.of(), null);
    }
}
