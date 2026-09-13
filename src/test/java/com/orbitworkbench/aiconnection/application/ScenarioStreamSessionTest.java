package com.orbitworkbench.aiconnection.application;

import com.orbitworkbench.ai.application.*;
import com.orbitworkbench.aiconnection.domain.AiScenario;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ScenarioStreamSessionTest {
    @Test
    void totalDeadlineStopsEvenContinuouslyProducingStream() {
        var router = mock(AiScenarioRouter.class);
        var audit = mock(AiCallAuditRecorder.class);
        var gateway = mock(ModelGateway.class);
        var connection = new AiConnectionService.AiConnectionRuntimeConfig(
                5L, 9L, "test", "https://example.invalid/v1", "/chat/completions",
                "CHAT_COMPLETIONS", "test", "test-placeholder", 60000);
        when(router.resolve(any(), any(), any())).thenReturn(
                new AiScenarioRouter.ResolvedRoute(connection, null, false, AiScenarioRouter.SOURCE_PINNED));
        when(gateway.stream(any())).thenReturn(Flux.interval(Duration.ofMillis(5))
                .map(ignored -> AiStreamEvent.delta("token")));
        var session = new AiScenarioExecutionService(router, audit, gateway).stream(
                AiScenario.INTERVIEW_REPORT, 7L, null, "system", "prompt", 512, Duration.ofMillis(100));
        session.begin();
        var failure = assertThrows(RuntimeException.class,
                () -> session.deltas().blockLast(Duration.ofSeconds(3)));
        assertEquals(com.orbitworkbench.shared.api.ErrorCode.REQUEST_TIMEOUT,
                session.fail(failure, 0).getErrorCode());
        session.fail(failure, 0);
        verify(audit, times(1)).finish(any(), any(), any(), eq("FAILED"), any(),
                anyInt(), anyInt(), anyInt(), any(), any(), any(), anyBoolean());
    }
}
