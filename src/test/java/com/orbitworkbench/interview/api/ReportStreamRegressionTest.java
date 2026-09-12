package com.orbitworkbench.interview.api;

import com.orbitworkbench.ai.application.*;
import com.orbitworkbench.aiconnection.application.*;
import com.orbitworkbench.aiconnection.application.AiConnectionService.AiConnectionRuntimeConfig;
import com.orbitworkbench.interview.application.*;
import com.orbitworkbench.interview.domain.InterviewSessionRecord;
import com.orbitworkbench.identity.application.OrbitUserDetails;
import com.orbitworkbench.identity.domain.AppUserRecord;
import com.orbitworkbench.shared.api.*;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import reactor.core.publisher.Flux;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.*;

class ReportStreamRegressionTest {
    private final AiScenarioRouter router = mock(AiScenarioRouter.class);
    private final AiCallAuditRecorder audit = mock(AiCallAuditRecorder.class);
    private final ModelGateway gateway = mock(ModelGateway.class);
    private final InterviewReportService reports = mock(InterviewReportService.class);
    private final InterviewSessionRecord interview = new InterviewSessionRecord();
    private final InterviewSessionController controller = new InterviewSessionController(
            mock(InterviewSessionService.class), reports, mock(InterviewQuestionService.class),
            new AiScenarioExecutionService(router, audit, gateway));

    @BeforeEach
    void setup() {
        var connection = new AiConnectionRuntimeConfig(
                5L, 9L, "test", "https://example.invalid/v1", "/chat/completions",
                "CHAT_COMPLETIONS", "test", "test-placeholder", 60000);
        when(router.resolve(any(), any(), any())).thenReturn(
                new AiScenarioRouter.ResolvedRoute(connection, null, false, AiScenarioRouter.SOURCE_PINNED));
        when(audit.start(any(), any(), any(), anyInt(), any())).thenReturn(77L);
        when(reports.prepareStream(7L, 21L, null)).thenReturn(
                new InterviewReportService.ReportStreamPreparation(interview, "prompt"));
        when(reports.systemPromptForStream()).thenReturn("system");
        when(reports.maxOutputTokensForStream()).thenReturn(512);
        when(reports.modelTimeoutForStream()).thenReturn(Duration.ofSeconds(5));
    }

    @Test
    void initializesRealStreamAndPersistsBeforeDoneAndAuditSuccess() {
        when(gateway.stream(any())).thenReturn(Flux.just(AiStreamEvent.delta("report")));
        var events = controller.generateReportStream(21L, null, principal()).collectList().block();
        assertEquals(List.of("delta", "done"), events.stream().map(e -> e.event()).toList());
        var order = inOrder(router, audit, gateway, reports);
        order.verify(router).resolve(any(), any(), any());
        order.verify(audit).start(any(), any(), any(), anyInt(), any());
        order.verify(gateway).stream(any());
        order.verify(reports).finalizeStreamedReport(interview, "report");
        order.verify(audit).finish(eq(77L), eq(7L), any(), eq("SUCCEEDED"), isNull(),
                anyInt(), anyInt(), eq(6), isNull(), isNull(), isNull(), eq(5L), eq(false));
    }

    @Test
    void routingRejectionDoesNotMarkReportFailedOrCallModel() {
        when(router.resolve(any(), any(), any())).thenThrow(new ApiException(
                HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST, "route unavailable"));
        var events = controller.generateReportStream(21L, null, principal()).collectList().block();
        assertEquals("error", events.getFirst().event());
        verifyNoInteractions(gateway);
        verify(reports, never()).markFailedAndNotifyForStream(any(), any());
    }

    @Test
    void upstreamFailureRecordsFailureAndNeverFinalizesPartialReport() {
        when(gateway.stream(any())).thenReturn(Flux.concat(
                Flux.just(AiStreamEvent.delta("partial")), Flux.error(new IllegalStateException("failed"))));
        var events = controller.generateReportStream(21L, null, principal()).collectList().block();
        assertEquals(List.of("delta", "error"), events.stream().map(e -> e.event()).toList());
        verify(reports).markFailedAndNotifyForStream(eq(interview), any());
        verify(reports, never()).finalizeStreamedReport(any(), any());
        verify(audit).finish(eq(77L), eq(7L), any(), eq("FAILED"), any(),
                anyInt(), anyInt(), eq(7), isNull(), isNull(), isNull(), eq(5L), eq(false));
    }

    @Test
    void parserFailureIsHandledExactlyOnce() {
        when(gateway.stream(any())).thenReturn(Flux.just(AiStreamEvent.delta("bad json")));
        when(reports.finalizeStreamedReport(interview, "bad json")).thenThrow(new ApiException(
                HttpStatus.BAD_GATEWAY, ErrorCode.INVALID_STRUCTURED_OUTPUT, "invalid report"));
        var events = controller.generateReportStream(21L, null, principal()).collectList().block();
        assertEquals("error", events.getLast().event());
        verify(reports, times(1)).markFailedAndNotifyForStream(eq(interview), any());
        verify(audit, times(1)).finish(any(), any(), any(), eq("FAILED"), any(),
                anyInt(), anyInt(), anyInt(), any(), anyBoolean());
    }

    private UsernamePasswordAuthenticationToken principal() {
        var user = new AppUserRecord();
        user.setId(7L);
        user.setUsername("test");
        return new UsernamePasswordAuthenticationToken(new OrbitUserDetails(user), null, List.of());
    }
}
