package com.orbitworkbench.ai.infrastructure.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.ai.application.AiInvocation;
import com.orbitworkbench.ai.application.AiProviderException;
import com.orbitworkbench.ai.application.AiStreamEvent;
import com.orbitworkbench.aiconnection.application.AiConnectionService.AiConnectionRuntimeConfig;
import com.orbitworkbench.shared.api.ErrorCode;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * A-04：通过真实 HTTP 传输层（{@link AbstractOpenAiCompatibleAdapter#open} 走 WebClient/Reactor Netty）
 * 验收 RESPONSES 协议与错误语义。既有的 {@code OpenAiCompatibleAdapterParsingTest} 只把 Flux 直接喂给
 * 解析器，从未覆盖 requestUri 拼接、状态码映射、超时与连接失败分类；本类补齐这些线上路径。
 */
class AiAdapterHttpTransportTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private OpenAiCompatibleResponsesAdapter responsesAdapter;
    private MockWebServer server;

    @BeforeEach
    void startServer() throws Exception {
        try {
            new JdkClientHttpConnector();
        } catch (RuntimeException exception) {
            assumeTrue(false, "Skipped: 当前 JVM 无法建立 loopback HTTP selector: "
                    + rootMessage(exception));
            return;
        }
        server = new MockWebServer();
        try {
            server.start();
        } catch (IOException exception) {
            server = null;
            assumeTrue(false, "Skipped: 当前环境无法启动本地 Mock HTTP 服务: "
                    + rootMessage(exception));
            return;
        }
        responsesAdapter = new OpenAiCompatibleResponsesAdapter(
                WebClient.builder().clientConnector(new JdkClientHttpConnector()),
                objectMapper);
    }

    @AfterEach
    void stopServer() throws Exception {
        if (server != null) {
            server.shutdown();
        }
    }

    @Test
    void responsesProtocolRoundTripsOverRealHttp() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "id": "resp_http_1",
                          "status": "completed",
                          "output_text": "hello from responses",
                          "output": [],
                          "usage": {"input_tokens": 5, "output_tokens": 3, "total_tokens": 8}
                        }
                        """));

        List<AiStreamEvent> events = responsesAdapter
                .open(invocation("RESPONSES", "/v1/responses", false, 5000))
                .events()
                .collectList()
                .block();

        assertEquals(List.of(
                "run.started",
                "output.text.delta",
                "output.text.completed",
                "usage.updated",
                "run.completed"), eventTypes(events));
        assertEquals("hello from responses", events.get(1).text());
        assertEquals("resp_http_1", events.get(4).providerRequestId());
        assertEquals(5, events.get(4).usage().inputTokens());
        assertEquals(3, events.get(4).usage().outputTokens());
        assertTrue(events.get(4).done());

        RecordedRequest request = server.takeRequest();
        assertEquals("/v1/responses", request.getPath());
        assertEquals("Bearer sk-test-secret-123", request.getHeader("Authorization"));
        JsonNode body = objectMapper.readTree(request.getBody().readUtf8());
        assertEquals("test-model", body.get("model").asText());
        assertEquals("system prompt", body.get("instructions").asText());
        assertEquals("user prompt", body.get("input").asText());
        assertEquals(512, body.get("max_output_tokens").asInt());
        assertTrue(body.get("store").isBoolean() && !body.get("store").asBoolean());
    }

    @Test
    void responsesStreamingAssemblesDeltasAndHonoursCompletedMarker() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sse("{\"type\":\"response.output_text.delta\",\"delta\":\"How \"}")
                        + sse("{\"type\":\"response.output_text.delta\",\"delta\":\"are you?\"}")
                        + sse("{\"type\":\"response.output_text.done\",\"text\":\"How are you?\"}")
                        + sse("{\"type\":\"response.completed\"}")));

        var call = responsesAdapter.open(invocation("RESPONSES", "/v1/responses", true, 5000));
        List<AiStreamEvent> events = call.events().collectList().block();

        assertEquals(List.of(
                "run.started",
                "output.text.delta",
                "output.text.delta",
                "output.text.completed",
                "run.completed"), eventTypes(events));
        StringBuilder reassembled = new StringBuilder();
        events.stream().filter(event -> "output.text.delta".equals(event.type()))
                .forEach(event -> reassembled.append(event.text()));
        assertEquals("How are you?", reassembled.toString());
        assertTrue(call.doneMarkerReceived());
        assertTrue(events.get(events.size() - 1).done());
    }

    @Test
    void responsesStreamingWithoutCompletedMarkerIsInterrupted() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sse("{\"type\":\"response.output_text.delta\",\"delta\":\"partial\"}")));

        AiProviderException exception = assertThrows(AiProviderException.class, () ->
                responsesAdapter.open(invocation("RESPONSES", "/v1/responses", true, 5000))
                        .events().collectList().block());

        assertEquals(ErrorCode.STREAM_INTERRUPTED, exception.getErrorCode());
    }

    @Test
    void upstreamAuthenticationFailureMapsToAuthenticationFailed() {
        server.enqueue(new MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"error\":{\"message\":\"invalid api key\"}}"));

        AiProviderException exception = assertThrows(AiProviderException.class, () ->
                responsesAdapter.open(invocation("RESPONSES", "/v1/responses", false, 5000))
                        .events().collectList().block());

        assertEquals(ErrorCode.AUTHENTICATION_FAILED, exception.getErrorCode());
        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatus());
        assertEquals(401, exception.getHttpStatus());
    }

    @Test
    void upstreamRateLimitMapsToTooManyRequests() {
        server.enqueue(new MockResponse()
                .setResponseCode(429)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"error\":{\"message\":\"slow down\"}}"));

        AiProviderException exception = assertThrows(AiProviderException.class, () ->
                responsesAdapter.open(invocation("RESPONSES", "/v1/responses", false, 5000))
                        .events().collectList().block());

        assertEquals(ErrorCode.RATE_LIMITED, exception.getErrorCode());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatus());
        assertEquals(429, exception.getHttpStatus());
    }

    @Test
    void upstreamServerErrorMapsToUpstreamUnavailable() {
        server.enqueue(new MockResponse().setResponseCode(503).setBody("bad gateway"));

        AiProviderException exception = assertThrows(AiProviderException.class, () ->
                responsesAdapter.open(invocation("RESPONSES", "/v1/responses", false, 5000))
                        .events().collectList().block());

        assertEquals(ErrorCode.UPSTREAM_UNAVAILABLE, exception.getErrorCode());
        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatus());
        assertEquals(503, exception.getHttpStatus());
    }

    @Test
    void perCallTimeoutMapsToGatewayTimeout() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\":\"slow\",\"status\":\"completed\",\"output_text\":\"late\"}")
                .setBodyDelay(1, TimeUnit.SECONDS));

        AiProviderException exception = assertThrows(AiProviderException.class, () ->
                responsesAdapter.open(invocation("RESPONSES", "/v1/responses", false, 200))
                        .events().collectList().block());

        assertEquals(ErrorCode.REQUEST_TIMEOUT, exception.getErrorCode());
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, exception.getStatus());
    }

    @Test
    void connectionRefusedMapsToUpstreamUnavailable() {
        // 端口 1 无监听：触发 WebClientRequestException（连接拒绝）分类，而非超时。
        AiProviderException exception = assertThrows(AiProviderException.class, () ->
                responsesAdapter.open(new AiInvocation(
                                new AiConnectionRuntimeConfig(1L, 2L, "http-test",
                                        "http://127.0.0.1:1", "/v1/responses", "RESPONSES",
                                        "test-model", "sk-test-secret-123", 5000),
                                "system prompt", "user prompt", 10L, 20L, false, 512))
                        .events().collectList().block());

        assertEquals(ErrorCode.UPSTREAM_UNAVAILABLE, exception.getErrorCode());
        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatus());
    }

    @Test
    void blankStructuredOutputIsRejectedOverHttp() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\":\"empty\",\"status\":\"completed\",\"output_text\":\"\",\"output\":[]}"));

        AiProviderException exception = assertThrows(AiProviderException.class, () ->
                responsesAdapter.open(invocation("RESPONSES", "/v1/responses", false, 5000))
                        .events().collectList().block());

        assertEquals(ErrorCode.INVALID_STRUCTURED_OUTPUT, exception.getErrorCode());
    }

    @Test
    void unparseableSuccessBodyMapsToUnknownProviderError() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("this is not json"));

        AiProviderException exception = assertThrows(AiProviderException.class, () ->
                responsesAdapter.open(invocation("RESPONSES", "/v1/responses", false, 5000))
                        .events().collectList().block());

        assertEquals(ErrorCode.UNKNOWN_PROVIDER_ERROR, exception.getErrorCode());
    }

    private AiInvocation invocation(String protocol, String endpointPath, boolean stream, int timeoutMs) {
        AiConnectionRuntimeConfig connection = new AiConnectionRuntimeConfig(
                1L, 2L, "http-test", localServerUrl(), endpointPath, protocol,
                "test-model", "sk-test-secret-123", timeoutMs);
        return new AiInvocation(connection, "system prompt", "user prompt", 10L, 20L, stream, 512);
    }

    private String localServerUrl() {
        return "http://127.0.0.1:" + server.getPort();
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static String sse(String data) {
        return "data: " + data + "\n\n";
    }

    private static List<String> eventTypes(List<AiStreamEvent> events) {
        return events.stream().map(AiStreamEvent::type).toList();
    }
}
