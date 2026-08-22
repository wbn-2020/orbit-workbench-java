package com.orbitworkbench.ai.application;

import java.util.List;

public record AiConversationItem(
        String role,
        String content,
        List<AiToolCall> toolCalls,
        String toolCallId,
        String toolName
) {

    public static AiConversationItem user(String content) {
        return new AiConversationItem("user", content, List.of(), null, null);
    }

    public static AiConversationItem assistant(String content,
                                               List<AiToolCall> toolCalls) {
        return new AiConversationItem(
                "assistant",
                content,
                toolCalls == null ? List.of() : List.copyOf(toolCalls),
                null,
                null);
    }

    public static AiConversationItem toolResult(String toolCallId,
                                                String toolName,
                                                String content) {
        return new AiConversationItem(
                "tool",
                content,
                List.of(),
                toolCallId,
                toolName);
    }
}
