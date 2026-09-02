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
public class OpenAiCompatibleResponsesAdapter
        extends AbstractOpenAiCompatibleAdapter {

    public OpenAiCompatibleResponsesAdapter(WebClient.Builder webClientBuilder,
                                            ObjectMapper objectMapper) {
        super(webClientBuilder, objectMapper);
    }

    @Override
    public String protocol() {
        return "RESPONSES";
    }

    @Override
    protected Map<String, Object> requestBody(AiInvocation invocation) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", invocation.connection().modelName());
        body.put("instructions", invocation.systemPrompt());
        body.put("input", responsesInput(invocation));
        body.put("stream", invocation.stream());
        body.put("max_output_tokens", invocation.maxOutputTokens());
        body.put("store", false);
        if (invocation.previousResponseId() != null
                && !invocation.previousResponseId().isBlank()) {
            body.put("previous_response_id", invocation.previousResponseId());
        }
        List<Map<String, Object>> tools = new ArrayList<>(invocation.tools().stream()
                .map(this::responsesTool)
                .toList());
        // 内置网页检索与函数工具共用同一个 tools 数组，两个都要发时不能各写一次互相覆盖。
        WebSearchMode mode = invocation.webSearch();
        WebSearchDialect dialect = invocation.connection().webSearchDialect();
        if (mode != WebSearchMode.DISABLED) {
            if (dialect != WebSearchDialect.RESPONSES_TOOL && dialect != WebSearchDialect.RESPONSES_TOOL_FORCED) {
                throw new AiProviderException(ErrorCode.UNSUPPORTED_CAPABILITY,
                        HttpStatus.UNPROCESSABLE_ENTITY, null,
                        "连接声明的联网形状 " + dialect + " 不适用于 Responses 协议", null);
            }
            tools.add(Map.of("type", "web_search"));
            if (mode == WebSearchMode.ON_DEMAND) {
                if (dialect != WebSearchDialect.RESPONSES_TOOL_FORCED) {
                    throw new AiProviderException(ErrorCode.UNSUPPORTED_CAPABILITY,
                            HttpStatus.UNPROCESSABLE_ENTITY, null,
                            "该联网形状只能由模型自行决定是否检索，无法表达「必须联网」", null);
                }
                body.put("tool_choice", Map.of("type", "web_search"));
            } else {
                body.put("tool_choice", "auto");
            }
        }
        if (!tools.isEmpty()) {
            body.put("tools", tools);
        }
        return body;
    }

    @Override
    protected List<AiStreamEvent> parseNonStreaming(String body, AiInvocation invocation) {
        JsonNode root = AiJsonSupport.read(objectMapper, body);
        String providerRequestId = AiJsonSupport.providerRequestId(root);
        String text = AiJsonSupport.text(root, "output_text");
        if (text == null) {
            text = responseOutputText(AiJsonSupport.node(root, "output"));
        }
        List<AiToolCall> toolCalls = responseToolCalls(
                AiJsonSupport.node(root, "output"));
        if ((text == null || text.isBlank()) && toolCalls.isEmpty()) {
            throw invalidOutput(providerRequestId);
        }
        if (!"completed".equalsIgnoreCase(AiJsonSupport.text(root, "status"))) {
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
        AtomicReference<String> requestId = new AtomicReference<>();
        AtomicReference<AiUsage> lastUsage = new AtomicReference<>();
        Map<String, ResponseToolCallAccumulator> toolCalls = new LinkedHashMap<>();

        Flux<AiStreamEvent> payload = events.concatMap(serverEvent -> {
            String data = serverEvent.data();
            if (data == null || data.isBlank()) {
                return Flux.empty();
            }
            if ("[DONE]".equals(data.trim())) {
                return Flux.empty();
            }
            JsonNode root = AiJsonSupport.read(objectMapper, data);
            String providerRequestId = AiJsonSupport.providerRequestId(root);
            if (providerRequestId != null) {
                requestId.set(providerRequestId);
            }
            String type = AiJsonSupport.text(root, "type");
            String text = AiJsonSupport.text(root, "delta");
            if (text == null) {
                text = AiJsonSupport.text(root, "output_text");
            }
            AiUsage usage = AiJsonSupport.usage(root);
            List<AiStreamEvent> mapped = new ArrayList<>();
            if (text != null && !text.isBlank()) {
                textSeen.set(true);
                mapped.add(event("output.text.delta", text, null,
                        requestId.get(), false));
            }
            if (usage != null) {
                lastUsage.set(usage);
                mapped.add(event("usage.updated", null, usage,
                        requestId.get(), false));
            }
            collectResponseToolCall(toolCalls, root, type);
            if ("response.output_text.done".equals(type)) {
                mapped.add(event("output.text.completed", null, usage,
                        requestId.get(), false));
            }
            if ("response.completed".equals(type)) {
                call.markDoneMarkerReceived();
                terminationSeen.set(true);
                mapped.addAll(completeResponseToolCalls(
                        toolCalls, requestId.get(), toolSeen, toolCallsEmitted));
            }
            if ("response.failed".equals(type) || "response.error".equals(type)) {
                String message = AiJsonSupport.text(root, "error", "message");
                if (message == null) {
                    message = "Responses 上游调用失败";
                }
                return Flux.error(new com.orbitworkbench.ai.application.AiProviderException(
                        com.orbitworkbench.shared.api.ErrorCode.UNKNOWN_PROVIDER_ERROR,
                        org.springframework.http.HttpStatus.BAD_GATEWAY,
                        call.httpStatus(),
                        com.orbitworkbench.ai.application.AiErrorSanitizer.sanitize(
                                message, invocation.connection().apiKey()),
                        requestId.get()));
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

    private Object responsesInput(AiInvocation invocation) {
        if (invocation.conversation().isEmpty()) {
            return invocation.userPrompt();
        }
        List<Map<String, Object>> input = new ArrayList<>();
        for (AiConversationItem item : invocation.conversation()) {
            if ("tool".equals(item.role())) {
                input.add(Map.of(
                        "type", "function_call_output",
                        "call_id", item.toolCallId(),
                        "output", item.content()));
                continue;
            }
            if (item.toolCalls() != null && !item.toolCalls().isEmpty()) {
                for (AiToolCall toolCall : item.toolCalls()) {
                    input.add(Map.of(
                            "type", "function_call",
                            "call_id", toolCall.id(),
                            "name", toolCall.name(),
                            "arguments", toolCall.argumentsJson()));
                }
            }
            if (item.content() != null && !item.content().isBlank()) {
                input.add(Map.of(
                        "role", item.role(),
                        "content", item.content()));
            }
        }
        return input;
    }

    private Map<String, Object> responsesTool(AiToolDefinition definition) {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("type", "function");
        tool.put("name", definition.name());
        if (definition.description() != null) {
            tool.put("description", definition.description());
        }
        tool.put("parameters", definition.parameters());
        tool.put("strict", true);
        return tool;
    }

    private List<AiToolCall> responseToolCalls(JsonNode output) {
        if (output == null || !output.isArray()) {
            return List.of();
        }
        List<AiToolCall> result = new ArrayList<>();
        int index = 0;
        for (JsonNode item : output) {
            if (!"function_call".equals(AiJsonSupport.text(item, "type"))) {
                index++;
                continue;
            }
            String id = firstNonBlank(
                    AiJsonSupport.text(item, "call_id"),
                    AiJsonSupport.text(item, "id"),
                    "call_" + index);
            String name = AiJsonSupport.text(item, "name");
            JsonNode arguments = AiJsonSupport.node(item, "arguments");
            if (name != null && arguments != null) {
                result.add(new AiToolCall(
                        id,
                        name,
                        arguments.isTextual() ? arguments.asText() : arguments.toString()));
            }
            index++;
        }
        return result;
    }

    private void collectResponseToolCall(
            Map<String, ResponseToolCallAccumulator> accumulators,
            JsonNode root,
            String type) {
        JsonNode item = AiJsonSupport.node(root, "item");
        String key = firstNonBlank(
                AiJsonSupport.text(root, "item_id"),
                AiJsonSupport.text(item, "id"),
                AiJsonSupport.text(root, "call_id"),
                String.valueOf(AiJsonSupport.integer(root, "output_index")));
        if (key == null || "null".equals(key)) {
            return;
        }
        ResponseToolCallAccumulator accumulator =
                accumulators.computeIfAbsent(key, ignored -> new ResponseToolCallAccumulator());
        if ("response.output_item.added".equals(type)
                || "response.output_item.done".equals(type)) {
            if (item != null && "function_call".equals(AiJsonSupport.text(item, "type"))) {
                accumulator.merge(
                        firstNonBlank(
                                AiJsonSupport.text(item, "call_id"),
                                AiJsonSupport.text(item, "id")),
                        AiJsonSupport.text(item, "name"),
                        AiJsonSupport.text(item, "arguments"),
                        false);
            }
        }
        if ("response.function_call_arguments.delta".equals(type)) {
            accumulator.merge(
                    AiJsonSupport.text(root, "call_id"),
                    AiJsonSupport.text(root, "name"),
                    AiJsonSupport.text(root, "delta"),
                    true);
        }
        if ("response.function_call_arguments.done".equals(type)) {
            accumulator.merge(
                    AiJsonSupport.text(root, "call_id"),
                    AiJsonSupport.text(root, "name"),
                    AiJsonSupport.text(root, "arguments"),
                    false);
        }
    }

    private List<AiStreamEvent> completeResponseToolCalls(
            Map<String, ResponseToolCallAccumulator> accumulators,
            String requestId,
            AtomicBoolean toolSeen,
            AtomicBoolean emitted) {
        if (!emitted.compareAndSet(false, true)) {
            return List.of();
        }
        List<AiStreamEvent> result = new ArrayList<>();
        int index = 0;
        for (ResponseToolCallAccumulator accumulator : accumulators.values()) {
            AiToolCall toolCall = accumulator.finish(index++);
            if (toolCall != null) {
                toolSeen.set(true);
                result.add(AiStreamEvent.toolCall(toolCall, requestId));
            }
        }
        return result;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static final class ResponseToolCallAccumulator {
        private String callId;
        private String name;
        private final StringBuilder arguments = new StringBuilder();

        private void merge(String newCallId,
                           String newName,
                           String newArguments,
                           boolean append) {
            if (newCallId != null && !newCallId.isBlank()) {
                callId = newCallId;
            }
            if (newName != null && !newName.isBlank()) {
                name = newName;
            }
            if (newArguments != null) {
                if (!append) {
                    arguments.setLength(0);
                }
                arguments.append(newArguments);
            }
        }

        private AiToolCall finish(int index) {
            if (name == null || name.isBlank()) {
                return null;
            }
            return new AiToolCall(
                    callId == null || callId.isBlank() ? "call_" + index : callId,
                    name,
                    arguments.isEmpty() ? "{}" : arguments.toString());
        }
    }

    private String responseOutputText(JsonNode output) {
        if (output == null || !output.isArray()) {
            return null;
        }
        for (JsonNode item : output) {
            String text = AiJsonSupport.firstText(AiJsonSupport.node(item, "content"));
            if (text != null && !text.isBlank()) {
                return text;
            }
        }
        return null;
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
                "上游未返回 Responses 完成事件",
                providerRequestId);
    }
}
