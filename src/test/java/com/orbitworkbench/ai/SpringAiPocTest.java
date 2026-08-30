package com.orbitworkbench.ai;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.web.client.RestClient;

/**
 * Spring AI PoC（V-001 待确认事项验证，默认跳过：-Dspring.ai.poc=true 启用）。
 * 用本地 Mock 网关模拟 Chat Completions 协议，验证 Spring AI 是否满足本项目约束：
 * 自定义完整 Base URL 原样调用、Chat Completions 协议、流式事件。
 * 结论回填 07 §16 与 ADR。
 */
class SpringAiPocTest {

    private static HttpServer gateway;
    private static int port;

    @BeforeAll
    static void startMockGateway() throws java.io.IOException {
        gateway = HttpServer.create(new InetSocketAddress(0), 0);
        port = gateway.getAddress().getPort();
        gateway.createContext("/v1/chat/completions", exchange -> {
            byte[] body = ("{\"id\":\"chatcmpl-poc\",\"object\":\"chat.completion\",\"created\":1,"
                    + "\"model\":\"poc-model\",\"choices\":[{\"index\":0,"
                    + "\"message\":{\"role\":\"assistant\",\"content\":\"POC-OK\"},"
                    + "\"finish_reason\":\"stop\"}],"
                    + "\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        gateway.start();
    }

    @AfterAll
    static void stopGateway() {
        if (gateway != null) {
            gateway.stop(0);
        }
    }

    @Test
    void springAiSupportsCustomBaseUrlAndChatCompletions() {
        // Spring AI 默认在 baseUrl 后追加完整路径 /v1/chat/completions（PoC 关键发现：
        // 与本项目“完整 Base URL 原样调用”约定不同，需要把用户 URL 拆分为 host 与路径两段）。
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl("http://127.0.0.1:" + port)
                .apiKey("sk-poc-not-a-real-key")
                .restClientBuilder(RestClient.builder())
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
}
