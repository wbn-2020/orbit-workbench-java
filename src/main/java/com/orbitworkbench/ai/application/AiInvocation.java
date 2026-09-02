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
        String previousResponseId,
        WebSearchMode webSearch
) {
    public AiInvocation {
        if (maxOutputTokens <= 0) {
            throw new IllegalArgumentException("maxOutputTokens 必须大于 0");
        }
        tools = tools == null ? List.of() : List.copyOf(tools);
        conversation = conversation == null ? List.of() : List.copyOf(conversation);
        // 缺省必须是 DISABLED：没显式开联网的调用，请求体要与改动前逐字节一致。
        webSearch = webSearch == null ? WebSearchMode.DISABLED : webSearch;
    }

    public AiInvocation(AiConnectionRuntimeConfig connection,
                        String systemPrompt,
                        String userPrompt,
                        Long runId,
                        Long modelCallId,
                        boolean stream,
                        int maxOutputTokens) {
        this(connection, systemPrompt, userPrompt, runId, modelCallId, stream,
                maxOutputTokens, List.of(), List.of(), null, WebSearchMode.DISABLED);
    }

    /** 适配器只按解析后的结论发参数，降级判断不在这里重复一遍。 */
    public AiInvocation withWebSearch(WebSearchMode mode) {
        return new AiInvocation(connection, systemPrompt, userPrompt, runId, modelCallId, stream,
                maxOutputTokens, tools, conversation, previousResponseId, mode);
    }
}
