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
        body.put("input", invocation.userPrompt());
        body.put("stream", invocation.stream());
        body.put("max_output_tokens", invocation.maxOutputTokens());
        body.put("store", false);
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
        if (text == null || text.isBlank()) {
            throw invalidOutput(providerRequestId);
        }
        if (!"completed".equalsIgnoreCase(AiJsonSupport.text(root, "status"))) {
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
            if ("response.output_text.done".equals(type)) {
                mapped.add(event("output.text.completed", null, usage,
                        requestId.get(), false));
            }
            if ("response.completed".equals(type)) {
                call.markDoneMarkerReceived();
                terminationSeen.set(true);
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
                    if (!textSeen.get()) {
                        return Flux.error(invalidOutput(requestId.get()));
                    }
                    return Flux.just(event("run.completed", null, lastUsage.get(),
                            requestId.get(), true));
                }));
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
