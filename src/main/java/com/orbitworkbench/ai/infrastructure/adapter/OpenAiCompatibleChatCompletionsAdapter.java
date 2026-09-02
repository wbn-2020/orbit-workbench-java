package com.orbitworkbench.ai.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.ai.application.AiAdapterCall;
import com.orbitworkbench.ai.application.AiConversationItem;
import com.orbitworkbench.ai.application.AiInvocation;
import com.orbitworkbench.ai.application.AiProviderException;
import com.orbitworkbench.ai.application.AiStreamEvent;
import com.orbitworkbench.ai.application.AiToolCall;
import com.orbitworkbench.ai.application.AiToolDefinition;
import com.orbitworkbench.ai.application.AiUsage;
import com.orbitworkbench.ai.application.WebSearchDialect;
import com.orbitworkbench.ai.application.WebSearchMode;
import com.orbitworkbench.shared.api.ErrorCode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

@Component
public class OpenAiCompatibleChatCompletionsAdapter
        extends AbstractOpenAiCompatibleAdapter {

    public OpenAiCompatibleChatCompletionsAdapter(WebClient.Builder webClientBuilder,
                                                  ObjectMapper objectMapper) {
        super(webClientBuilder, objectMapper);
    }

    @Override
    public String protocol() {
        return "CHAT_COMPLETIONS";
    }

    @Override
    protected Map<String, Object> requestBody(AiInvocation invocation) {
        List<Map<String, Object>> messages = new ArrayList<>();
        if (invocation.systemPrompt() != null && !invocation.systemPrompt().isBlank()) {
            messages.add(Map.of("role", "system", "content", invocation.systemPrompt()));
        }
        if (invocation.conversation().isEmpty()) {
            messages.add(Map.of("role", "user", "content", invocation.userPrompt()));
        } else {
            for (AiConversationItem item : invocation.conversation()) {
                messages.add(chatMessage(item));
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", invocation.connection().modelName());
        body.put("messages", messages);
        body.put("stream", invocation.stream());
        body.put("max_tokens", invocation.maxOutputTokens());
        if (!invocation.tools().isEmpty()) {
            body.put("tools", invocation.tools().stream()
                    .map(this::chatTool)
                    .toList());
            body.put("tool_choice", "auto");
        }
        applyWebSearch(body, invocation);
        return body;
    }

    /**
     * 联网参数按连接声明的形状填（ADR-0012）。Chat Completions 这一路三家参数名完全不同，
     * 所以形状是数据不是 if(域名)：业务层永远不会出现供应商参数名。
     *
     * <p>声明与协议不符时直接报错，不能"跳过不发"——静默跳过正是本次要消除的那个
     * 「选择器有值、上游没行为」的形态。
     */
    private void applyWebSearch(Map<String, Object> body, AiInvocation invocation) {
        WebSearchMode mode = invocation.webSearch();
        WebSearchDialect dialect = invocation.connection().webSearchDialect();
        if (mode == WebSearchMode.DISABLED) {
            return;
        }
        boolean forced = mode == WebSearchMode.ON_DEMAND;
        switch (dialect) {
            case OPENAI_CHAT_WEB_SEARCH_OPTIONS -> {
                Map<String, Object> options = new LinkedHashMap<>();
                options.put("search_context_size", "medium");
                body.put("web_search_options", options);
            }
            case QWEN_CHAT_ENABLE_SEARCH -> {
                body.put("enable_search", true);
                if (forced) {
                    body.put("search_options", Map.of("forced_search", true));
                }
            }
            case XAI_CHAT_SEARCH_PARAMETERS -> body.put("search_parameters",
                    Map.of("mode", forced ? "on" : "auto"));
            default -> throw new AiProviderException(ErrorCode.UNSUPPORTED_CAPABILITY,
                    HttpStatus.UNPROCESSABLE_ENTITY, null,
                    "连接声明的联网形状 " + dialect + " 不适用于 Chat Completions 协议", null);
        }
    }

    @Override
    protected List<AiStreamEvent> parseNonStreaming(String body, AiInvocation invocation) {
        JsonNode root = AiJsonSupport.read(objectMapper, body);
        String providerRequestId = AiJsonSupport.providerRequestId(root);
        JsonNode message = AiJsonSupport.node(root, "choices", "0", "message");
        String text = AiJsonSupport.text(message, "content");
        if (text == null) {
            text = AiJsonSupport.firstText(message == null ? null : message.get("content"));
        }
        List<AiToolCall> toolCalls = parseToolCalls(
                message == null ? null : message.get("tool_calls"));
        if ((text == null || text.isBlank()) && toolCalls.isEmpty()) {
            throw invalidOutput(providerRequestId);
        }
        String finishReason = AiJsonSupport.text(root, "choices", "0", "finish_reason");
        if (finishReason == null || finishReason.isBlank()) {
            throw incompleteResponse(providerRequestId);
        }
        AiUsage usage = AiJsonSupport.usage(root);
        List<AiStreamEvent> result = new ArrayList<>();
        result.add(event("run.started", null, null, providerRequestId, false));
        if (text != null && !text.isBlank()) {
            result.add(event("output.text.delta", text, null, providerRequestId, false));
            result.add(event("output.text.completed", null, null, providerRequestId, false));
        }
        toolCalls.forEach(toolCall ->
                result.add(AiStreamEvent.toolCall(toolCall, providerRequestId)));
        if (usage != null) {
            result.add(event("usage.updated", null, usage, providerRequestId, false));
        }
        result.add(event("run.completed", null, usage, providerRequestId, true));
        return result;
    }

    @Override
    protected Flux<AiStreamEvent> parseStreaming(
            Flux<ServerSentEvent<String>> events,
            AiInvocation invocation,
            AiAdapterCall call) {
        AtomicBoolean terminationSeen = new AtomicBoolean();
        AtomicBoolean textSeen = new AtomicBoolean();
        AtomicBoolean toolSeen = new AtomicBoolean();
        AtomicBoolean toolCallsEmitted = new AtomicBoolean();
        AtomicBoolean textCompleted = new AtomicBoolean();
        AtomicReference<String> requestId = new AtomicReference<>();
        AtomicReference<AiUsage> lastUsage = new AtomicReference<>();
        Map<Integer, ToolCallAccumulator> toolCalls = new LinkedHashMap<>();

        Flux<AiStreamEvent> payload = events.concatMap(serverEvent -> {
            String data = serverEvent.data();
            if (data == null || data.isBlank()) {
                return Flux.empty();
            }
            if ("[DONE]".equals(data.trim())) {
                call.markDoneMarkerReceived();
                terminationSeen.set(true);
                return Flux.fromIterable(completeToolCalls(
                        toolCalls, requestId.get(), toolSeen, toolCallsEmitted));
            }
            JsonNode root = AiJsonSupport.read(objectMapper, data);
            String providerRequestId = AiJsonSupport.providerRequestId(root);
            if (providerRequestId != null) {
                requestId.set(providerRequestId);
            }
            String text = AiJsonSupport.text(root, "choices", "0", "delta", "content");
            if (text == null) {
                JsonNode choices = AiJsonSupport.node(root, "choices");
                if (choices != null && choices.isArray() && !choices.isEmpty()) {
                    text = AiJsonSupport.firstText(choices.get(0).get("delta"));
                }
            }
            AiUsage usage = AiJsonSupport.usage(root);
            List<AiStreamEvent> mapped = new ArrayList<>();
            if (text != null && !text.isBlank()) {
                textSeen.set(true);
                mapped.add(event("output.text.delta", text, null,
                        requestId.get(), false));
            }
            JsonNode deltas = AiJsonSupport.node(root, "choices", "0", "delta", "tool_calls");
            mergeToolCallDeltas(toolCalls, deltas);
            if (usage != null) {
                lastUsage.set(usage);
                mapped.add(event("usage.updated", null, usage,
                        requestId.get(), false));
            }
            String finishReason = AiJsonSupport.text(root, "choices", "0", "finish_reason");
            if (finishReason != null && !finishReason.isBlank()) {
                if (textSeen.get() && textCompleted.compareAndSet(false, true)) {
                    mapped.add(event("output.text.completed", null, usage,
                            requestId.get(), false));
                }
                mapped.addAll(completeToolCalls(
                        toolCalls, requestId.get(), toolSeen, toolCallsEmitted));
            }
            return Flux.fromIterable(mapped);
        });

        return Flux.concat(
                Flux.just(event("run.started", null, null, null, false)),
                payload,
                Flux.defer(() -> {
                    if (!terminationSeen.get()) {
                        return Flux.error(incompleteResponse(requestId.get()));
                    }
                    if (!textSeen.get() && !toolSeen.get()) {
                        return Flux.error(invalidOutput(requestId.get()));
                    }
                    return Flux.just(event("run.completed", null, lastUsage.get(),
                            requestId.get(), true));
                }));
    }

    private Map<String, Object> chatMessage(AiConversationItem item) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", item.role());
        if ("tool".equals(item.role())) {
            message.put("tool_call_id", item.toolCallId());
            message.put("content", item.content());
            return message;
        }
        message.put("content", item.content());
        if (item.toolCalls() != null && !item.toolCalls().isEmpty()) {
            message.put("tool_calls", item.toolCalls().stream()
                    .map(this::assistantToolCall)
                    .toList());
        }
        return message;
    }

    private Map<String, Object> chatTool(AiToolDefinition definition) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", definition.name());
        if (definition.description() != null) {
            function.put("description", definition.description());
        }
        function.put("parameters", definition.parameters());
        return Map.of("type", "function", "function", function);
    }

    private Map<String, Object> assistantToolCall(AiToolCall toolCall) {
        return Map.of(
                "id", toolCall.id(),
                "type", "function",
                "function", Map.of(
                        "name", toolCall.name(),
                        "arguments", toolCall.argumentsJson()));
    }

    private List<AiToolCall> parseToolCalls(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<AiToolCall> result = new ArrayList<>();
        int index = 0;
        for (JsonNode item : node) {
            String id = AiJsonSupport.text(item, "id");
            String name = AiJsonSupport.text(item, "function", "name");
            JsonNode arguments = AiJsonSupport.node(item, "function", "arguments");
            if (name != null && arguments != null) {
                result.add(new AiToolCall(
                        id == null ? "call_" + index : id,
                        name,
                        arguments.isTextual() ? arguments.asText() : arguments.toString()));
            }
            index++;
        }
        return result;
    }

    private void mergeToolCallDeltas(Map<Integer, ToolCallAccumulator> accumulators,
                                     JsonNode node) {
        if (node == null || !node.isArray()) {
            return;
        }
        int fallbackIndex = 0;
        for (JsonNode item : node) {
            Integer index = AiJsonSupport.integer(item, "index");
            int normalizedIndex = index == null ? fallbackIndex : index;
            ToolCallAccumulator accumulator = accumulators.computeIfAbsent(
                    normalizedIndex, ignored -> new ToolCallAccumulator());
            accumulator.merge(
                    AiJsonSupport.text(item, "id"),
                    AiJsonSupport.text(item, "function", "name"),
                    AiJsonSupport.text(item, "function", "arguments"));
            fallbackIndex++;
        }
    }

    private List<AiStreamEvent> completeToolCalls(
            Map<Integer, ToolCallAccumulator> accumulators,
            String requestId,
            AtomicBoolean toolSeen,
            AtomicBoolean emitted) {
        if (!emitted.compareAndSet(false, true)) {
            return List.of();
        }
        List<AiStreamEvent> result = new ArrayList<>();
        for (Map.Entry<Integer, ToolCallAccumulator> entry : accumulators.entrySet()) {
            AiToolCall toolCall = entry.getValue().finish(entry.getKey());
            if (toolCall != null) {
                toolSeen.set(true);
                result.add(AiStreamEvent.toolCall(toolCall, requestId));
            }
        }
        return result;
    }

    private static final class ToolCallAccumulator {
        private String id;
        private String name;
        private final StringBuilder arguments = new StringBuilder();

        private void merge(String idDelta, String nameDelta, String argumentsDelta) {
            if (idDelta != null && !idDelta.isBlank()) {
                id = idDelta;
            }
            if (nameDelta != null && !nameDelta.isBlank()) {
                name = nameDelta;
            }
            if (argumentsDelta != null) {
                arguments.append(argumentsDelta);
            }
        }

        private AiToolCall finish(int index) {
            if (name == null || name.isBlank()) {
                return null;
            }
            return new AiToolCall(
                    id == null || id.isBlank() ? "call_" + index : id,
                    name,
                    arguments.isEmpty() ? "{}" : arguments.toString());
        }
    }

    private AiProviderException invalidOutput(String providerRequestId) {
        return new AiProviderException(
                ErrorCode.INVALID_STRUCTURED_OUTPUT,
                HttpStatus.BAD_GATEWAY,
                null,
                "上游未返回非空文本内容",
                providerRequestId);
    }

    private AiProviderException incompleteResponse(String providerRequestId) {
        return new AiProviderException(
                ErrorCode.STREAM_INTERRUPTED,
                HttpStatus.BAD_GATEWAY,
                null,
                "上游未返回 Chat 完成标记",
                providerRequestId);
    }
}
