package com.orbitworkbench.ai.infrastructure.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.ai.application.AiAdapterCall;
import com.orbitworkbench.ai.application.AiInvocation;
import com.orbitworkbench.ai.application.AiProviderException;
import com.orbitworkbench.ai.application.AiStreamEvent;
import com.orbitworkbench.aiconnection.application.AiConnectionService.AiConnectionRuntimeConfig;
import com.orbitworkbench.shared.api.ErrorCode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

class OpenAiCompatibleAdapterParsingTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OpenAiCompatibleChatCompletionsAdapter chatAdapter =
            new OpenAiCompatibleChatCompletionsAdapter(WebClient.builder(), objectMapper);
    private final OpenAiCompatibleResponsesAdapter responsesAdapter =
            new OpenAiCompatibleResponsesAdapter(WebClient.builder(), objectMapper);

    @Test
    void chatRequestAndNonStreamingResponseUseCompatibleShape() {
        AiInvocation invocation = invocation("CHAT_COMPLETIONS", false);

        Map<String, Object> request = chatAdapter.requestBody(invocation);
        assertEquals("test-model", request.get("model"));
        assertEquals(false, request.get("stream"));
        assertEquals(512, request.get("max_tokens"));
        @SuppressWarnings("unchecked")
        List<Map<String, String>> messages =
                (List<Map<String, String>>) request.get("messages");
        assertEquals(List.of(
                Map.of("role", "system", "content", "system prompt"),
                Map.of("role", "user", "content", "user prompt")), messages);

        List<AiStreamEvent> events = chatAdapter.parseNonStreaming("""
                {
                  "id": "chatcmpl_123",
                  "choices": [{
                    "message": {"content": "hello"},
                    "finish_reason": "stop"
                  }],
                  "usage": {
                    "prompt_tokens": 7,
                    "completion_tokens": 3,
                    "total_tokens": 10
                  }
                }
                """, invocation);

        assertEquals(List.of(
                "run.started",
                "output.text.delta",
                "output.text.completed",
                "usage.updated",
                "run.completed"), eventTypes(events));
        assertEquals("hello", events.get(1).text());
        assertEquals("chatcmpl_123", events.get(4).providerRequestId());
        assertEquals(7, events.get(4).usage().inputTokens());
        assertEquals(3, events.get(4).usage().outputTokens());
        assertTrue(events.get(4).done());
    }

    @Test
    void chatStreamingRequiresDoneMarkerForRunCompletion() {
        AiInvocation invocation = invocation("CHAT_COMPLETIONS", true);
        AiAdapterCall completedCall = new AiAdapterCall();

        List<AiStreamEvent> completed = chatAdapter.parseStreaming(Flux.just(
                        sse("""
                                {"id":"chatcmpl_123","choices":[{"delta":{"content":"hel"}}]}
                                """),
                        sse("""
                                {"id":"chatcmpl_123","choices":[{"delta":{"content":"lo"},"finish_reason":"stop"}]}
                                """),
                        sse("[DONE]")),
                invocation, completedCall).collectList().block();

        assertEquals(List.of(
                "run.started",
                "output.text.delta",
                "output.text.delta",
                "output.text.completed",
                "run.completed"), eventTypes(completed));
        assertTrue(completedCall.doneMarkerReceived());
        assertTrue(completed.get(completed.size() - 1).done());

        AiAdapterCall interruptedCall = new AiAdapterCall();
        AiProviderException interrupted = assertThrows(AiProviderException.class, () ->
                chatAdapter.parseStreaming(Flux.just(
                                sse("""
                                        {"id":"chatcmpl_456","choices":[{"delta":{"content":"partial"}}]}
                                        """)),
                        invocation, interruptedCall).collectList().block());

        assertEquals(ErrorCode.STREAM_INTERRUPTED, interrupted.getErrorCode());
        assertFalse(interruptedCall.doneMarkerReceived());
    }

    @Test
    void chatRejectsEmptyOutputAndMissingCompletionMarker() {
        AiInvocation nonStreaming = invocation("CHAT_COMPLETIONS", false);

        AiProviderException empty = assertThrows(AiProviderException.class, () ->
                chatAdapter.parseNonStreaming("""
                        {
                          "id": "chatcmpl_empty",
                          "choices": [{"message": {"content": "  "}, "finish_reason": "stop"}]
                        }
                        """, nonStreaming));
        assertEquals(ErrorCode.INVALID_STRUCTURED_OUTPUT, empty.getErrorCode());

        AiProviderException incomplete = assertThrows(AiProviderException.class, () ->
                chatAdapter.parseNonStreaming("""
                        {
                          "id": "chatcmpl_incomplete",
                          "choices": [{"message": {"content": "answer"}}]
                        }
                        """, nonStreaming));
        assertEquals(ErrorCode.STREAM_INTERRUPTED, incomplete.getErrorCode());

        AiProviderException markerOnly = assertThrows(AiProviderException.class, () ->
                chatAdapter.parseStreaming(Flux.just(sse("[DONE]")),
                        invocation("CHAT_COMPLETIONS", true),
                        new AiAdapterCall()).collectList().block());
        assertEquals(ErrorCode.INVALID_STRUCTURED_OUTPUT, markerOnly.getErrorCode());
    }

    @Test
    void responsesRequestAndNonStreamingResponseUseCompatibleShape() {
        AiInvocation invocation = invocation("RESPONSES", false);

        Map<String, Object> request = responsesAdapter.requestBody(invocation);
        assertEquals("test-model", request.get("model"));
        assertEquals("system prompt", request.get("instructions"));
        assertEquals("user prompt", request.get("input"));
        assertEquals(false, request.get("stream"));
        assertEquals(false, request.get("store"));
        assertEquals(512, request.get("max_output_tokens"));

        List<AiStreamEvent> events = responsesAdapter.parseNonStreaming("""
                {
                  "id": "resp_123",
                  "status": "completed",
                  "output": [{
                    "type": "message",
                    "content": [{"type": "output_text", "text": "answer"}]
                  }],
                  "usage": {
                    "input_tokens": 11,
                    "output_tokens": 5,
                    "total_tokens": 16
                  }
                }
                """, invocation);

        assertEquals(List.of(
                "run.started",
                "output.text.delta",
                "output.text.completed",
                "usage.updated",
                "run.completed"), eventTypes(events));
        assertEquals("answer", events.get(1).text());
        assertEquals("resp_123", events.get(4).providerRequestId());
        assertEquals(11, events.get(4).usage().inputTokens());
        assertEquals(5, events.get(4).usage().outputTokens());
        assertTrue(events.get(4).done());
    }

    @Test
    void responsesStreamingMapsDeltaUsageAndCompletion() {
        AiInvocation invocation = invocation("RESPONSES", true);
        AiAdapterCall call = new AiAdapterCall();

        List<AiStreamEvent> events = responsesAdapter.parseStreaming(Flux.just(
                        sse("""
                                {"type":"response.output_text.delta","response":{"id":"resp_456"},"delta":"part"}
                                """),
                        sse("""
                                {"type":"response.output_text.done","response":{"id":"resp_456"}}
                                """),
                        sse("""
                                {
                                  "type":"response.completed",
                                  "response":{
                                    "id":"resp_456",
                                    "usage":{"input_tokens":4,"output_tokens":2,"total_tokens":6}
                                  }
                                }
                                """)),
                invocation, call).collectList().block();

        assertEquals(List.of(
                "run.started",
                "output.text.delta",
                "output.text.completed",
                "usage.updated",
                "run.completed"), eventTypes(events));
        assertEquals("part", events.get(1).text());
        assertEquals("resp_456", events.get(4).providerRequestId());
        assertEquals(4, events.get(4).usage().inputTokens());
        assertEquals(2, events.get(4).usage().outputTokens());
        assertTrue(events.get(4).done());
        assertTrue(call.doneMarkerReceived());
        assertNull(events.get(2).text());
    }

    @Test
    void responsesRejectEmptyOutputAndNonProtocolCompletion() {
        AiInvocation nonStreaming = invocation("RESPONSES", false);

        AiProviderException empty = assertThrows(AiProviderException.class, () ->
                responsesAdapter.parseNonStreaming("""
                        {
                          "id": "resp_empty",
                          "status": "completed",
                          "output": []
                        }
                        """, nonStreaming));
        assertEquals(ErrorCode.INVALID_STRUCTURED_OUTPUT, empty.getErrorCode());

        AiProviderException incomplete = assertThrows(AiProviderException.class, () ->
                responsesAdapter.parseNonStreaming("""
                        {
                          "id": "resp_incomplete",
                          "status": "in_progress",
                          "output_text": "answer"
                        }
                        """, nonStreaming));
        assertEquals(ErrorCode.STREAM_INTERRUPTED, incomplete.getErrorCode());

        AiProviderException doneMarkerOnly = assertThrows(AiProviderException.class, () ->
                responsesAdapter.parseStreaming(Flux.just(
                                sse("""
                                        {"type":"response.output_text.delta","response":{"id":"resp_789"},"delta":"answer"}
                                        """),
                                sse("[DONE]")),
                        invocation("RESPONSES", true),
                        new AiAdapterCall()).collectList().block());
        assertEquals(ErrorCode.STREAM_INTERRUPTED, doneMarkerOnly.getErrorCode());

        AiProviderException completedWithoutText = assertThrows(AiProviderException.class, () ->
                responsesAdapter.parseStreaming(Flux.just(
                                sse("""
                                        {"type":"response.completed","response":{"id":"resp_empty"}}
                                        """)),
                        invocation("RESPONSES", true),
                        new AiAdapterCall()).collectList().block());
        assertEquals(ErrorCode.INVALID_STRUCTURED_OUTPUT, completedWithoutText.getErrorCode());
    }

    private AiInvocation invocation(String protocol, boolean streaming) {
        return new AiInvocation(
                new AiConnectionRuntimeConfig(
                        1L,
                        2L,
                        "test connection",
                        "https://example.invalid",
                        "/v1/test",
                        protocol,
                        "test-model",
                        "test-secret",
                        30000),
                "system prompt",
                "user prompt",
                3L,
                4L,
                streaming,
                512);
    }

    private ServerSentEvent<String> sse(String data) {
        return ServerSentEvent.builder(data.strip()).build();
    }

    private List<String> eventTypes(List<AiStreamEvent> events) {
        return events.stream().map(AiStreamEvent::type).toList();
    }
}
