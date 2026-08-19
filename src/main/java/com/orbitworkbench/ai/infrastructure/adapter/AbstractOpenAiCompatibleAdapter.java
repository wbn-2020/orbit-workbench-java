package com.orbitworkbench.ai.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.ai.application.AiAdapterCall;
import com.orbitworkbench.ai.application.AiErrorSanitizer;
import com.orbitworkbench.ai.application.AiInvocation;
import com.orbitworkbench.ai.application.AiProviderException;
import com.orbitworkbench.ai.application.AiStreamEvent;
import com.orbitworkbench.shared.api.ErrorCode;
import java.time.Duration;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

public abstract class AbstractOpenAiCompatibleAdapter
        implements com.orbitworkbench.ai.application.AiProtocolAdapter {

    private static final ParameterizedTypeReference<ServerSentEvent<String>> SSE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    protected final WebClient webClient;
    protected final ObjectMapper objectMapper;

    protected AbstractOpenAiCompatibleAdapter(WebClient.Builder webClientBuilder,
                                              ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public AiAdapterCall open(AiInvocation invocation) {
        AiAdapterCall call = new AiAdapterCall();
        Flux<AiStreamEvent> events = Flux.defer(() -> webClient.post()
                        .uri(requestUri(invocation))
                        .contentType(MediaType.APPLICATION_JSON)
                        .headers(headers -> {
                            headers.setBearerAuth(invocation.connection().apiKey());
                            headers.set("Accept", invocation.stream()
                                    ? "text/event-stream" : "application/json");
                        })
                        .bodyValue(requestBody(invocation))
                        .exchangeToFlux(response -> handleResponse(response, invocation, call)))
                .timeout(Duration.ofMillis(invocation.connection().timeoutMs()))
                .onErrorMap(error -> mapFailure(error, invocation));
        call.setEvents(events);
        return call;
    }

    protected abstract Map<String, Object> requestBody(AiInvocation invocation);

    protected abstract java.util.List<AiStreamEvent> parseNonStreaming(
            String body, AiInvocation invocation);

    protected abstract Flux<AiStreamEvent> parseStreaming(
            Flux<ServerSentEvent<String>> events,
            AiInvocation invocation,
            AiAdapterCall call);

    private Flux<AiStreamEvent> handleResponse(ClientResponse response,
                                                AiInvocation invocation,
                                                AiAdapterCall call) {
        int status = response.statusCode().value();
        call.setHttpStatus(status);
        if (response.statusCode().isError()) {
            return response.bodyToMono(String.class)
                    .defaultIfEmpty("")
                    .flatMapMany(body -> Flux.error(upstreamError(status, body, invocation)));
        }
        if (invocation.stream()) {
            return parseStreaming(response.bodyToFlux(SSE_TYPE), invocation, call);
        }
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(body -> parseNonStreaming(body, invocation))
                .flatMapMany(Flux::fromIterable);
    }

    private AiProviderException upstreamError(int status,
                                              String body,
                                              AiInvocation invocation) {
        ErrorMapping mapping = mapStatus(status);
        String summary = AiJsonSupport.errorSummary(
                objectMapper, body, invocation.connection().apiKey());
        String message = summary == null || summary.isBlank()
                ? mapping.defaultMessage() : summary;
        return new AiProviderException(
                mapping.errorCode(),
                mapping.status(),
                status,
                message,
                null);
    }

    private Throwable mapFailure(Throwable error, AiInvocation invocation) {
        if (error instanceof AiProviderException) {
            return error;
        }
        Throwable cause = error;
        if (cause instanceof WebClientRequestException requestException
                && requestException.getCause() != null) {
            cause = requestException.getCause();
        }
        ErrorCode code;
        HttpStatus status;
        String message;
        if (cause instanceof TimeoutException) {
            code = ErrorCode.REQUEST_TIMEOUT;
            status = HttpStatus.GATEWAY_TIMEOUT;
            message = "模型请求超时";
        } else if (cause instanceof CancellationException) {
            code = ErrorCode.CANCELLED;
            status = HttpStatus.REQUEST_TIMEOUT;
            message = "模型请求已取消";
        } else if (error instanceof WebClientRequestException) {
            code = ErrorCode.UPSTREAM_UNAVAILABLE;
            status = HttpStatus.BAD_GATEWAY;
            message = "无法连接到上游模型服务";
        } else {
            code = ErrorCode.UNKNOWN_PROVIDER_ERROR;
            status = HttpStatus.BAD_GATEWAY;
            message = AiErrorSanitizer.sanitize(error.getMessage(),
                    invocation.connection().apiKey());
            if (message == null || message.isBlank()) {
                message = "上游模型服务调用失败";
            }
        }
        return new AiProviderException(
                code,
                status,
                null,
                message,
                null,
                error);
    }

    private ErrorMapping mapStatus(int status) {
        if (status == 400 || status == 422) {
            return new ErrorMapping(ErrorCode.INVALID_REQUEST,
                    HttpStatus.BAD_GATEWAY, "上游拒绝了模型请求");
        }
        if (status == 401 || status == 403) {
            return new ErrorMapping(ErrorCode.AUTHENTICATION_FAILED,
                    HttpStatus.BAD_GATEWAY, "上游凭据未通过认证");
        }
        if (status == 404) {
            return new ErrorMapping(ErrorCode.MODEL_NOT_FOUND,
                    HttpStatus.BAD_GATEWAY, "上游模型或路径不存在");
        }
        if (status == 408) {
            return new ErrorMapping(ErrorCode.REQUEST_TIMEOUT,
                    HttpStatus.GATEWAY_TIMEOUT, "上游模型请求超时");
        }
        if (status == 429) {
            return new ErrorMapping(ErrorCode.RATE_LIMITED,
                    HttpStatus.TOO_MANY_REQUESTS, "上游模型请求受限");
        }
        if (status >= 500) {
            return new ErrorMapping(ErrorCode.UPSTREAM_UNAVAILABLE,
                    HttpStatus.BAD_GATEWAY, "上游模型服务不可用");
        }
        return new ErrorMapping(ErrorCode.UNKNOWN_PROVIDER_ERROR,
                HttpStatus.BAD_GATEWAY, "上游模型服务返回异常状态");
    }

    protected static AiStreamEvent event(String type,
                                         String text,
                                         com.orbitworkbench.ai.application.AiUsage usage,
                                         String providerRequestId,
                                         boolean done) {
        return new AiStreamEvent(type, text, usage, providerRequestId, done);
    }

    private URI requestUri(AiInvocation invocation) {
        String base = invocation.connection().baseUrl();
        String normalizedBase = base.endsWith("/")
                ? base.substring(0, base.length() - 1) : base;
        String endpoint = invocation.connection().endpointPath();
        String normalizedEndpoint = endpoint.startsWith("/") ? endpoint : "/" + endpoint;
        return URI.create(normalizedBase + normalizedEndpoint);
    }

    private record ErrorMapping(ErrorCode errorCode,
                                HttpStatus status,
                                String defaultMessage) {
    }
}
