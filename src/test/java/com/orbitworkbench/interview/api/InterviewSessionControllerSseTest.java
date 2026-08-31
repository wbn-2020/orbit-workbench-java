package com.orbitworkbench.interview.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.orbitworkbench.identity.application.OrbitUserDetails;
import com.orbitworkbench.identity.domain.AppUserRecord;
import com.orbitworkbench.interview.application.InterviewQuestionService;
import com.orbitworkbench.interview.application.InterviewReportService;
import com.orbitworkbench.interview.application.InterviewSessionService;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.shared.api.GlobalExceptionHandler;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Flux;

/**
 * SSE 出题端点的 HTTP 契约测试：只装配 controller，不起 Spring 上下文、不连数据库。
 * 断言事件名与顺序真的进入 text/event-stream 报文，且失败路径不会退化成 500。
 */
class InterviewSessionControllerSseTest {

    private static final String STREAM_URL =
            "/api/v1/interview-sessions/21/questions/next/stream";

    private InterviewQuestionService questionService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        questionService = mock(InterviewQuestionService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new InterviewSessionController(mock(InterviewSessionService.class),
                        mock(InterviewReportService.class), questionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                // Boot 会把 StringHttpMessageConverter 默认字符集改成 UTF-8；standalone 装配要显式对齐，
                // 否则 SSE 里的中文题目会被写成 "?"
                .setMessageConverters(new StringHttpMessageConverter(StandardCharsets.UTF_8),
                        new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Test
    void streamsStartDeltaDoneInOrderAsServerSentEvents() throws Exception {
        when(questionService.streamNext(eq(7L), eq(21L), any())).thenReturn(Flux.just(
                event("start", "{\"turnNo\":1,\"type\":\"MAIN\"}"),
                event("delta", "请介绍"),
                event("delta", "一个项目"),
                event("done", "{\"turnId\":88,\"turnNo\":1,\"question\":\"请介绍一个项目\"}")));

        String body = streamedBody();

        List<String> eventNames = body.lines()
                .filter(line -> line.startsWith("event:"))
                .map(line -> line.substring("event:".length()))
                .toList();
        assertEquals(List.of("start", "delta", "delta", "done"), eventNames);
        assertTrue(body.contains("data:请介绍"), body);
        assertTrue(body.contains("\"question\":\"请介绍一个项目\""), body);
        verify(questionService).streamNext(eq(7L), eq(21L), any());
    }

    @Test
    void modelSideFailureClosesStreamWith200AndErrorEventInsteadOf500() throws Exception {
        when(questionService.streamNext(eq(7L), eq(21L), any())).thenReturn(Flux.just(
                event("start", "{\"turnNo\":1,\"type\":\"MAIN\"}"),
                event("error", "模型连接不可用，请检查 AI 连接")));

        String body = streamedBody();

        assertTrue(body.contains("event:error"), body);
        assertTrue(body.contains("data:模型连接不可用，请检查 AI 连接"), body);
    }

    @Test
    void transportFailureAfterStartIsCarriedAsErrorEventNotBrokenStream() throws Exception {
        // 流内异常由服务层 onErrorResume 转成 error 事件；HTTP 层必须仍以 200 + 完整事件收尾
        when(questionService.streamNext(eq(7L), eq(21L), any())).thenReturn(Flux.just(
                event("start", "{\"turnNo\":1,\"type\":\"MAIN\"}"),
                event("error", "无法连接模型服务，请检查网络后重试")));

        String body = streamedBody();

        assertTrue(body.startsWith("event:start"), body);
        assertTrue(body.contains("event:error"), body);
    }

    @Test
    void stateConflictBeforeStreamingKeepsProblemJsonContract() throws Exception {
        when(questionService.streamNext(eq(7L), eq(21L), any())).thenThrow(new ApiException(
                HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                "会话未在进行中，无法出题：当前 PAUSED"));

        mockMvc.perform(post(STREAM_URL)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .principal(principal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"turnType\":\"MAIN\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("STATE_CONFLICT"))
                .andExpect(jsonPath("$.detail").value("会话未在进行中，无法出题：当前 PAUSED"));
    }

    @Test
    void rejectsIllegalTurnTypeBeforeTouchingTheService() throws Exception {
        mockMvc.perform(post(STREAM_URL)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .principal(principal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"turnType\":\"follow_up\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.VALIDATION_FAILED.name()));

        verify(questionService, never()).streamNext(any(), any(), any());
    }

    /** 走完 SSE 的异步派发，返回按 UTF-8 解码后的报文（MockHttpServletResponse 默认按 ISO-8859-1 解码）。 */
    private String streamedBody() throws Exception {
        MvcResult started = performStream().andExpect(request().asyncStarted()).andReturn();
        MvcResult done = mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andReturn();
        byte[] raw = done.getResponse().getContentAsByteArray();
        String body = done.getResponse().getContentAsString(StandardCharsets.UTF_8);
        java.nio.file.Files.write(java.nio.file.Path.of("target/sse-body-dump.txt"),
                ("RAW=" + java.util.Arrays.toString(raw) + "\nBODY=" + body)
                        .getBytes(StandardCharsets.UTF_8));
        return body;
    }

    private ResultActions performStream() throws Exception {
        return mockMvc.perform(post(STREAM_URL)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .principal(principal())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"turnType\":\"MAIN\"}"));
    }

    private ServerSentEvent<String> event(String name, String data) {
        return ServerSentEvent.builder(data).event(name).build();
    }

    /** standalone 装配没有 Security 过滤链，登录主语通过 request principal 注入。 */
    private UsernamePasswordAuthenticationToken principal() {
        AppUserRecord user = new AppUserRecord();
        user.setId(7L);
        user.setUsername("demo");
        return new UsernamePasswordAuthenticationToken(new OrbitUserDetails(user), "n/a", List.of());
    }
}
