package com.orbitworkbench.aiconnection.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orbitworkbench.ai.application.AiInvocation;
import com.orbitworkbench.ai.application.AiProviderException;
import com.orbitworkbench.ai.application.AiStreamEvent;
import com.orbitworkbench.ai.application.ModelGateway;
import com.orbitworkbench.aiconnection.application.AiConnectionService.AiConnectionRuntimeConfig;
import com.orbitworkbench.aiconnection.application.AiScenarioRouter.ResolvedRoute;
import com.orbitworkbench.aiconnection.domain.AiScenario;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiScenarioExecutionServiceTest {

    private static final AiConnectionRuntimeConfig PRIMARY = config(5L, "主用账户");
    private static final AiConnectionRuntimeConfig BACKUP = config(6L, "备用账户");
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Mock
    private AiScenarioRouter router;

    @Mock
    private AiCallAuditRecorder recorder;

    @Mock
    private ModelGateway modelGateway;

    private AiScenarioExecutionService service;

    @BeforeEach
    void setUp() {
        service = new AiScenarioExecutionService(router, recorder, modelGateway);
        when(router.resolve(any(), any(), any())).thenReturn(
                new ResolvedRoute(PRIMARY, null, false, AiScenarioRouter.SOURCE_DEFAULT));
        when(recorder.start(any(), any(), any(), anyInt(), any())).thenReturn(55L);
        when(recorder.snapshotJson(any(), any(), anyInt(), anyBoolean())).thenReturn("{}");
    }

    @Test
    void successReturnsTextAndClosesAuditAsSucceeded() {
        stubStreams(textFlux("参考答案"));

        String output = service.executeText(AiScenario.KNOWLEDGE_ANSWER, 7L, null,
                "system", "user", 512, TIMEOUT);

        assertEquals("参考答案", output);
        verify(recorder).finish(eq(55L), eq(7L), eq(AiScenario.KNOWLEDGE_ANSWER),
                eq(AiCallAuditRecorder.STATUS_SUCCEEDED), isNull(), anyInt(), eq(10),
                eq(4), isNull(), isNull(), eq(5L), eq(false));
    }

    @Test
    void providerErrorCodeIsPassedThroughInsteadOfFlatBadGateway() {
        stubStreams(Flux.error(new AiProviderException(ErrorCode.AUTHENTICATION_FAILED,
                HttpStatus.FORBIDDEN, 401, "上游鉴权失败", null)));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.executeText(AiScenario.INTERVIEW_QUESTION, 7L, null,
                        "system", "user", 512, TIMEOUT));

        assertEquals(ErrorCode.AUTHENTICATION_FAILED, exception.getErrorCode());
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertTrue(exception.getMessage().contains("AUTHENTICATION_FAILED"),
                "错误信息应保留原始错误码，实际：" + exception.getMessage());
        verify(recorder).finish(eq(55L), eq(7L), eq(AiScenario.INTERVIEW_QUESTION),
                eq(AiCallAuditRecorder.STATUS_FAILED), eq("AUTHENTICATION_FAILED"),
                anyInt(), anyInt(), anyInt(), isNull(), isNull(), eq(5L), eq(false));
    }

    @Test
    void switchableFailureRetriesOnBackupAndMarksBackupAttempted() {
        when(router.resolve(any(), any(), any())).thenReturn(
                new ResolvedRoute(PRIMARY, BACKUP, true, AiScenarioRouter.SOURCE_ROUTE));
        when(modelGateway.stream(any(AiInvocation.class)))
                .thenAnswer(invocation -> Flux.error(new AiProviderException(
                        ErrorCode.UPSTREAM_UNAVAILABLE, HttpStatus.BAD_GATEWAY, 502, "上游不可用", null)))
                .thenAnswer(invocation -> textFlux("备用答案"));

        String output = service.executeText(AiScenario.INTERVIEW_REPORT, 7L, null,
                "system", "user", 512, TIMEOUT);

        assertEquals("备用答案", output);
        verify(modelGateway, times(2)).stream(any(AiInvocation.class));
        verify(recorder).finish(eq(55L), eq(7L), eq(AiScenario.INTERVIEW_REPORT),
                eq(AiCallAuditRecorder.STATUS_SUCCEEDED), isNull(), anyInt(), anyInt(),
                anyInt(), isNull(), isNull(), eq(6L), eq(true));
    }

    @Test
    void nonSwitchableFailureNeverTouchesBackup() {
        when(router.resolve(any(), any(), any())).thenReturn(
                new ResolvedRoute(PRIMARY, BACKUP, true, AiScenarioRouter.SOURCE_ROUTE));
        stubStreams(Flux.error(new AiProviderException(ErrorCode.MODEL_NOT_FOUND,
                HttpStatus.NOT_FOUND, 404, "模型不存在", null)));

        assertThrows(ApiException.class, () -> service.executeText(
                AiScenario.INTERVIEW_REPORT, 7L, null, "system", "user", 512, TIMEOUT));

        verify(modelGateway, times(1)).stream(any(AiInvocation.class));
        verify(recorder).finish(eq(55L), eq(7L), eq(AiScenario.INTERVIEW_REPORT),
                eq(AiCallAuditRecorder.STATUS_FAILED), eq("MODEL_NOT_FOUND"),
                anyInt(), anyInt(), anyInt(), isNull(), isNull(), eq(5L), eq(false));
    }

    @Test
    void failoverSwitchDisabledKeepsSingleAttempt() {
        when(router.resolve(any(), any(), any())).thenReturn(
                new ResolvedRoute(PRIMARY, BACKUP, false, AiScenarioRouter.SOURCE_ROUTE));
        stubStreams(Flux.error(new AiProviderException(ErrorCode.RATE_LIMITED,
                HttpStatus.TOO_MANY_REQUESTS, 429, "限流", null)));

        assertThrows(ApiException.class, () -> service.executeText(
                AiScenario.PROJECT_FACT, 7L, null, "system", "user", 512, TIMEOUT));

        verify(modelGateway, times(1)).stream(any(AiInvocation.class));
    }

    @Test
    void bothAttemptsFailingReportsBackupErrorCode() {
        when(router.resolve(any(), any(), any())).thenReturn(
                new ResolvedRoute(PRIMARY, BACKUP, true, AiScenarioRouter.SOURCE_ROUTE));
        when(modelGateway.stream(any(AiInvocation.class)))
                .thenAnswer(invocation -> Flux.error(new AiProviderException(
                        ErrorCode.REQUEST_TIMEOUT, HttpStatus.GATEWAY_TIMEOUT, 504, "超时", null)))
                .thenAnswer(invocation -> Flux.error(new AiProviderException(
                        ErrorCode.STREAM_INTERRUPTED, HttpStatus.BAD_GATEWAY, null, "流中断", null)));

        ApiException exception = assertThrows(ApiException.class, () -> service.executeText(
                AiScenario.INTERVIEW_QUESTION, 7L, null, "system", "user", 512, TIMEOUT));

        assertEquals(ErrorCode.STREAM_INTERRUPTED, exception.getErrorCode());
        verify(recorder).finish(eq(55L), eq(7L), eq(AiScenario.INTERVIEW_QUESTION),
                eq(AiCallAuditRecorder.STATUS_FAILED), eq("STREAM_INTERRUPTED"),
                anyInt(), anyInt(), anyInt(), isNull(), isNull(), eq(6L), eq(true));
    }

    @Test
    void emptyOutputFailsAsStructuredOutputWithoutFailover() {
        when(router.resolve(any(), any(), any())).thenReturn(
                new ResolvedRoute(PRIMARY, BACKUP, true, AiScenarioRouter.SOURCE_ROUTE));
        stubStreams(Flux.just(new AiStreamEvent(
                "finish", null, null, null, null, true, null, null, null)));

        ApiException exception = assertThrows(ApiException.class, () -> service.executeText(
                AiScenario.KNOWLEDGE_ANSWER, 7L, null, "system", "user", 512, TIMEOUT));

        assertEquals(ErrorCode.INVALID_STRUCTURED_OUTPUT, exception.getErrorCode());
        verify(modelGateway, times(1)).stream(any(AiInvocation.class));
        verify(recorder).finish(eq(55L), eq(7L), eq(AiScenario.KNOWLEDGE_ANSWER),
                eq(AiCallAuditRecorder.STATUS_FAILED), eq("INVALID_STRUCTURED_OUTPUT"),
                anyInt(), anyInt(), eq(0), isNull(), isNull(), eq(5L), eq(false));
    }

    @Test
    void blockingGatewayTimeoutMapsToRequestTimeout() {
        // blockLast(Duration) 超时抛 IllegalStateException，而不是 AiProviderException
        stubStreams(Flux.never());

        ApiException exception = assertThrows(ApiException.class, () -> service.executeText(
                AiScenario.KNOWLEDGE_ANSWER, 7L, null, "system", "user", 512,
                Duration.ofMillis(50)));

        assertEquals(ErrorCode.REQUEST_TIMEOUT, exception.getErrorCode());
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, exception.getStatus());
    }

    @Test
    void missingAuditRowDoesNotBreakModelCall() {
        // 审计写失败时 recorder.start 返回 null（异常在写入侧已吞掉），业务链路必须照常完成
        when(recorder.start(any(), any(), any(), anyInt(), any())).thenReturn(null);
        stubStreams(textFlux("仍然回答"));

        String output = service.executeText(AiScenario.KNOWLEDGE_ANSWER, 7L, null,
                "system", "user", 512, TIMEOUT);

        assertEquals("仍然回答", output);
        verify(recorder).finish(isNull(), eq(7L), eq(AiScenario.KNOWLEDGE_ANSWER),
                eq(AiCallAuditRecorder.STATUS_SUCCEEDED), isNull(), anyInt(), anyInt(),
                anyInt(), isNull(), isNull(), eq(5L), eq(false));
    }

    private void stubStreams(Flux<AiStreamEvent> single) {
        when(modelGateway.stream(any(AiInvocation.class))).thenReturn(single);
    }

    private Flux<AiStreamEvent> textFlux(String text) {
        return Flux.just(
                new AiStreamEvent("start", null, null, null, null, false, null, null, null),
                new AiStreamEvent("delta", text, null, null, null, false, null, null, null),
                new AiStreamEvent("finish", null, null, null, null, true, null, null, null));
    }

    private static AiConnectionRuntimeConfig config(Long id, String name) {
        return new AiConnectionRuntimeConfig(id, 9L, name, "https://gw.example/v1",
                "/chat/completions", "CHAT_COMPLETIONS", "model-x", "sk-test", 60000);
    }
}
