package com.orbitworkbench.ai;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Spring AI PoC（V-001 待确认事项验证，默认跳过：-Dspring.ai.poc=true 启用）。
 * 用本地 Mock 网关模拟 Chat Completions 协议，验证 Spring AI 是否满足本项目约束：
 * 自定义完整 Base URL 原样调用、Chat Completions 协议、流式事件。
 * 结论回填 07 §16 与 ADR。
 */
@EnabledIfSystemProperty(named = "spring.ai.poc", matches = "true")
class SpringAiPocTest {

    private static MockWebServer gateway;

    @BeforeAll
    static void startMockGateway() throws IOException {
        try {
            new JdkClientHttpRequestFactory();
        } catch (RuntimeException exception) {
            assumeTrue(false, "Skipped: 当前 JVM 无法建立 loopback HTTP selector: "
                    + rootMessage(exception));
            return;
        }
        gateway = new MockWebServer();
        gateway.start();
        gateway.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"id":"chatcmpl-poc","object":"chat.completion","created":1,
                         "model":"poc-model","choices":[{"index":0,
                         "message":{"role":"assistant","content":"POC-OK"},
                         "finish_reason":"stop"}],
                         "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                        """));
        gateway.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"id":"chatcmpl-poc-stream","object":"chat.completion","created":1,
                         "model":"poc-model","choices":[{"index":0,
                         "message":{"role":"assistant","content":"POC-OK"},
                         "finish_reason":"stop"}],
                         "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                        """));
    }

    @AfterAll
    static void stopGateway() {
        if (gateway != null) {
            try {
                gateway.shutdown();
            } catch (java.io.IOException ignored) {
                // Test cleanup must not hide the assertion result.
            }
        }
    }

    @Test
    void springAiSupportsCustomBaseUrlAndChatCompletions() {
        // Spring AI 默认在 baseUrl 后追加完整路径 /v1/chat/completions（PoC 关键发现：
        // 与本项目“完整 Base URL 原样调用”约定不同，需要把用户 URL 拆分为 host 与路径两段）。
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl("http://127.0.0.1:" + gateway.getPort())
                .apiKey("sk-poc-not-a-real-key")
                .restClientBuilder(RestClient.builder()
                        .requestFactory(new JdkClientHttpRequestFactory()))
                .build();
        OpenAiChatModel model = OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(OpenAiChatOptions.builder().model("poc-model").build())
                .build();

        ChatResponse response = model.call(new Prompt("只回复 POC-OK"));

        assertNotNull(response);
        String text = response.getResult() == null ? null
                : (response.getResult().getOutput() instanceof AssistantMessage message
                        ? message.getText() : null);
        assertNotNull(text, "模型应返回文本");
        assertTrue(text.contains("POC-OK"), "应包含 Mock 网关返回内容，实际：" + text);

        List<ChatResponse> stream = model.stream(new Prompt("流式验证"))
                .collectList()
                .block(java.time.Duration.ofSeconds(10));
        assertNotNull(stream, "流式调用应有响应");
        assertTrue(stream.size() >= 1, "流式应至少一帧");
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
