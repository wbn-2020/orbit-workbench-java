package com.orbitworkbench.ai.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.ai.application.AiAdapterCall;
import com.orbitworkbench.ai.application.AiInvocation;
import com.orbitworkbench.ai.application.AiProviderException;
import com.orbitworkbench.ai.application.AiStreamEvent;
import com.orbitworkbench.ai.application.AiUsage;
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
        List<Map<String, String>> messages = new ArrayList<>();
        if (invocation.systemPrompt() != null && !invocation.systemPrompt().isBlank()) {
            messages.add(Map.of("role", "system", "content", invocation.systemPrompt()));
        }
        messages.add(Map.of("role", "user", "content", invocation.userPrompt()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", invocation.connection().modelName());
        body.put("messages", messages);
        body.put("stream", invocation.stream());
        body.put("max_tokens", invocation.maxOutputTokens());
        return body;
    }

    @Override
    protected List<AiStreamEvent> parseNonStreaming(String body, AiInvocation invocation) {
        JsonNode root = AiJsonSupport.read(objectMapper, body);
        String providerRequestId = AiJsonSupport.providerRequestId(root);
        String text = AiJsonSupport.text(root, "choices", "0", "message", "content");
        if (text == null) {
            JsonNode choices = AiJsonSupport.node(root, "choices");
            if (choices != null && choices.isArray() && !choices.isEmpty()) {
                text = AiJsonSupport.firstText(choices.get(0).get("message"));
            }
        }
        if (text == null || text.isBlank()) {
            throw invalidOutput(providerRequestId);
        }
        String finishReason = AiJsonSupport.text(root, "choices", "0", "finish_reason");
        if (finishReason == null || finishReason.isBlank()) {
            throw incompleteResponse(providerRequestId);
        }
        AiUsage usage = AiJsonSupport.usage(root);
        List<AiStreamEvent> result = new ArrayList<>();
        result.add(event("run.started", null, null, providerRequestId, false));
        result.add(event("output.text.delta", text, null, providerRequestId, false));
        result.add(event("output.text.completed", null, null, providerRequestId, false));
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
        AtomicReference<String> requestId = new AtomicReference<>();
        AtomicReference<AiUsage> lastUsage = new AtomicReference<>();

        Flux<AiStreamEvent> payload = events.concatMap(serverEvent -> {
            String data = serverEvent.data();
            if (data == null || data.isBlank()) {
                return Flux.empty();
            }
            if ("[DONE]".equals(data.trim())) {
                call.markDoneMarkerReceived();
                terminationSeen.set(true);
                return Flux.empty();
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
            if (usage != null) {
                lastUsage.set(usage);
                mapped.add(event("usage.updated", null, usage,
                        requestId.get(), false));
            }
            String finishReason = AiJsonSupport.text(root, "choices", "0", "finish_reason");
            if (finishReason != null && !finishReason.isBlank()) {
                mapped.add(event("output.text.completed", null, usage,
                        requestId.get(), false));
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
                    if (!textSeen.get()) {
                        return Flux.error(invalidOutput(requestId.get()));
                    }
                    return Flux.just(event("run.completed", null, lastUsage.get(),
                            requestId.get(), true));
                }));
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
