package com.orbitworkbench.ai.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

@Deprecated(forRemoval = true)
public class ModelGatewayImpl implements ModelGateway {
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    public ModelGatewayImpl(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
    }

    @Override
    public Flux<AiStreamEvent> stream(AiInvocation invocation) {
        return "RESPONSES".equalsIgnoreCase(invocation.connection().protocol())
                ? responses(invocation) : chatCompletions(invocation);
    }

    private Flux<AiStreamEvent> chatCompletions(AiInvocation invocation) {
        var c = invocation.connection();
        Map<String, Object> body = Map.of(
                "model", c.modelName(),
                "messages", List.of(
                        Map.of("role", "system", "content", invocation.systemPrompt()),
                        Map.of("role", "user", "content", invocation.userPrompt())
                ),
                "stream", invocation.stream(),
                "max_tokens", invocation.maxOutputTokens()
        );
        WebClient client = webClientBuilder.baseUrl(c.baseUrl()).build();
        var request = client.post().uri(c.endpointPath())
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth(c.apiKey()))
                .bodyValue(body)
                .retrieve()
                .onStatus(status -> status.isError(), this::mapError);
        if (!invocation.stream()) {
            return request.bodyToMono(String.class)
                    .timeout(Duration.ofMillis(c.timeoutMs()))
                    .map(this::parseChatResponse)
                    .flatMapMany(text -> Flux.just(AiStreamEvent.started(),
                            AiStreamEvent.delta(text), AiStreamEvent.completed(),
                            new AiStreamEvent("run.completed", null, null, null, null, true, null, null)));
        }
        return request.bodyToFlux(String.class)
                .timeout(Duration.ofMillis(c.timeoutMs()))
                .flatMapIterable(this::splitSse)
                .map(this::parseChatStream)
                .filter(event -> event != null)
                .startWith(AiStreamEvent.started())
                .concatWithValues(AiStreamEvent.completed(),
                        new AiStreamEvent("run.completed", null, null, null, null, true, null, null));
    }

    private Flux<AiStreamEvent> responses(AiInvocation invocation) {
        var c = invocation.connection();
        Map<String, Object> body = Map.of(
                "model", c.modelName(),
                "input", invocation.userPrompt(),
                "instructions", invocation.systemPrompt(),
                "stream", invocation.stream()
        );
        WebClient client = webClientBuilder.baseUrl(c.baseUrl()).build();
        var request = client.post().uri(c.endpointPath())
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth(c.apiKey()))
                .bodyValue(body)
                .retrieve()
                .onStatus(status -> status.isError(), this::mapError);
        if (!invocation.stream()) {
            return request.bodyToMono(String.class)
                    .timeout(Duration.ofMillis(c.timeoutMs()))
                    .map(this::parseResponsesResponse)
                    .flatMapMany(text -> Flux.just(AiStreamEvent.started(),
                            AiStreamEvent.delta(text), AiStreamEvent.completed(),
                            new AiStreamEvent("run.completed", null, null, null, null, true, null, null)));
        }
        return request.bodyToFlux(String.class)
                .timeout(Duration.ofMillis(c.timeoutMs()))
                .flatMapIterable(this::splitSse)
                .map(this::parseResponsesStream)
                .filter(event -> event != null)
                .startWith(AiStreamEvent.started())
                .concatWithValues(AiStreamEvent.completed(),
                        new AiStreamEvent("run.completed", null, null, null, null, true, null, null));
    }

    private reactor.core.publisher.Mono<? extends Throwable> mapError(ClientResponse response) {
        return response.bodyToMono(String.class).defaultIfEmpty("")
                .map(body -> new ApiException(mapStatus(response.statusCode()),
                        mapErrorCode(response.statusCode()), sanitize(body)));
    }

    private HttpStatus mapStatus(org.springframework.http.HttpStatusCode status) {
        if (status.value() == 401 || status.value() == 403) return HttpStatus.BAD_GATEWAY;
        if (status.value() == 404) return HttpStatus.BAD_GATEWAY;
        if (status.value() == 429) return HttpStatus.TOO_MANY_REQUESTS;
        return HttpStatus.BAD_GATEWAY;
    }

    private ErrorCode mapErrorCode(org.springframework.http.HttpStatusCode status) {
        return switch (status.value()) {
            case 401, 403 -> ErrorCode.AUTHENTICATION_FAILED;
            case 404 -> ErrorCode.MODEL_NOT_FOUND;
            case 429 -> ErrorCode.RATE_LIMITED;
            default -> ErrorCode.UNKNOWN_PROVIDER_ERROR;
        };
    }

    private String parseChatResponse(String raw) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            return root.path("choices").path(0).path("message").path("content").asText("");
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, ErrorCode.INVALID_REQUEST, "模型响应格式无效");
        }
    }

    private String parseResponsesResponse(String raw) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            if (root.has("output_text")) return root.path("output_text").asText("");
            JsonNode output = root.path("output");
            for (JsonNode item : output) {
                for (JsonNode content : item.path("content")) {
                    if (content.has("text")) return content.path("text").asText("");
                }
            }
            return "";
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, ErrorCode.INVALID_REQUEST, "模型响应格式无效");
        }
    }

    private AiStreamEvent parseChatStream(String line) {
        if (line == null || line.isBlank()) return null;
        String data = line.startsWith("data:") ? line.substring(5).trim() : line.trim();
        if ("[DONE]".equals(data)) return AiStreamEvent.completed();
        try {
            JsonNode root = objectMapper.readTree(data);
            String text = root.path("choices").path(0).path("delta").path("content").asText("");
            return text.isBlank() ? null : AiStreamEvent.delta(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private AiStreamEvent parseResponsesStream(String line) {
        if (line == null || line.isBlank()) return null;
        String data = line.startsWith("data:") ? line.substring(5).trim() : line.trim();
        try {
            JsonNode root = objectMapper.readTree(data);
            String type = root.path("type").asText("");
            String text = root.path("delta").asText("");
            if (text.isBlank()) text = root.path("text").asText("");
            if (type.contains("completed")) return AiStreamEvent.completed();
            return text.isBlank() ? null : AiStreamEvent.delta(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<String> splitSse(String chunk) {
        List<String> result = new ArrayList<>();
        for (String line : chunk.split("\\r?\\n")) {
            if (!line.isBlank()) result.add(line);
        }
        return result;
    }

    private String sanitize(String value) {
        String normalized = value == null ? "" : value.replaceAll("(?i)(authorization|api[-_ ]?key|token)\\s*[:=]\\s*[^,\\s]+", "$1=[REDACTED]");
        return normalized.length() > 512 ? normalized.substring(0, 512) : normalized;
    }
}
