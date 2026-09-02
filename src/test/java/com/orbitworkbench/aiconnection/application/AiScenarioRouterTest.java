package com.orbitworkbench.aiconnection.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orbitworkbench.aiconnection.api.AiConnectionDtos.ConnectionResponse;
import com.orbitworkbench.aiconnection.application.AiConnectionService.AiConnectionRuntimeConfig;
import com.orbitworkbench.aiconnection.application.AiScenarioRouter.ResolvedRoute;
import com.orbitworkbench.aiconnection.domain.AiScenario;
import com.orbitworkbench.aiconnection.domain.ScenarioRouteRecord;
import com.orbitworkbench.aiconnection.infrastructure.mapper.AiScenarioMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.shared.api.PageResult;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiScenarioRouterTest {

    @Mock
    private AiScenarioMapper mapper;

    @Mock
    private AiConnectionService connectionService;

    @InjectMocks
    private AiScenarioRouter router;

    @Test
    void pinnedConnectionWinsAndSkipsScenarioRoute() {
        stubConfig(5L, "快照账户");

        ResolvedRoute route = router.resolve(7L, AiScenario.INTERVIEW_QUESTION, 5L);

        assertEquals(5L, route.primary().connectionId());
        assertNull(route.backup());
        assertFalse(route.canFailover());
        assertEquals(AiScenarioRouter.SOURCE_PINNED, route.source());
        verify(mapper, never()).findRoute(any(), any());
    }

    @Test
    void configuredRouteSuppliesPrimaryAndBackupForFailover() {
        when(mapper.findRoute(7L, "INTERVIEW_REPORT")).thenReturn(route(5L, 6L, true));
        stubConfig(5L, "主用");
        stubConfig(6L, "备用");

        ResolvedRoute route = router.resolve(7L, AiScenario.INTERVIEW_REPORT);

        assertEquals(5L, route.primary().connectionId());
        assertEquals(6L, route.backup().connectionId());
        assertEquals(true, route.canFailover());
        assertEquals(AiScenarioRouter.SOURCE_ROUTE, route.source());
    }

    @Test
    void failoverStaysOffWhenSwitchIsDisabled() {
        when(mapper.findRoute(7L, "PROJECT_FACT")).thenReturn(route(5L, 6L, false));
        stubConfig(5L, "主用");
        stubConfig(6L, "备用");

        ResolvedRoute route = router.resolve(7L, AiScenario.PROJECT_FACT);

        assertEquals(6L, route.backup().connectionId());
        assertEquals(false, route.canFailover());
    }

    @Test
    void unusablePrimaryIsPromotedToBackupWithoutFailover() {
        when(mapper.findRoute(7L, "PROJECT_FACT")).thenReturn(route(5L, 6L, true));
        when(connectionService.getRuntimeConfig(5L)).thenThrow(new ApiException(
                HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT, "AI Connection 未启用"));
        stubConfig(6L, "备用");

        ResolvedRoute route = router.resolve(7L, AiScenario.PROJECT_FACT);

        assertEquals(6L, route.primary().connectionId());
        assertNull(route.backup());
        assertEquals(false, route.canFailover());
    }

    @Test
    void noRouteFallsBackToFirstEnabledConnection() {
        when(mapper.findRoute(7L, "KNOWLEDGE_ANSWER")).thenReturn(null);
        when(connectionService.list(true, 1, 1)).thenReturn(
                new PageResult<>(List.of(connection(7L, "最近更新账户")), 1, 1, 1));
        stubConfig(7L, "最近更新账户");

        ResolvedRoute route = router.resolve(7L, AiScenario.KNOWLEDGE_ANSWER);

        assertEquals(7L, route.primary().connectionId());
        assertEquals(AiScenarioRouter.SOURCE_DEFAULT, route.source());
        assertFalse(route.canFailover());
    }

    @Test
    void bothRouteConnectionsUnusableFallsBackToDefaultStrategy() {
        when(mapper.findRoute(7L, "PROJECT_FACT")).thenReturn(route(5L, 6L, true));
        when(connectionService.getRuntimeConfig(5L)).thenThrow(new ApiException(
                HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "AI Connection 不存在"));
        when(connectionService.getRuntimeConfig(6L)).thenThrow(new ApiException(
                HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "AI Connection 不存在"));
        when(connectionService.list(true, 1, 1)).thenReturn(
                new PageResult<>(List.of(connection(9L, "兜底账户")), 1, 1, 1));
        stubConfig(9L, "兜底账户");

        ResolvedRoute route = router.resolve(7L, AiScenario.PROJECT_FACT);

        assertEquals(9L, route.primary().connectionId());
        assertEquals(AiScenarioRouter.SOURCE_DEFAULT, route.source());
    }

    @Test
    void missingAnyConnectionReportsActionableConflict() {
        when(connectionService.list(true, 1, 1)).thenReturn(new PageResult<>(List.of(), 1, 1, 0));

        ApiException exception = assertThrows(ApiException.class,
                () -> router.resolve(7L, AiScenario.KNOWLEDGE_ANSWER));

        assertEquals(ErrorCode.STATE_CONFLICT, exception.getErrorCode());
        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
    }

    private void stubConfig(Long id, String name) {
        when(connectionService.getRuntimeConfig(id)).thenReturn(new AiConnectionRuntimeConfig(
                id, 9L, name, "https://gw.example/v1", "/chat/completions",
                "CHAT_COMPLETIONS", "model-x", "sk-test", 60000));
    }

    private ScenarioRouteRecord route(Long primary, Long backup, boolean failover) {
        ScenarioRouteRecord record = new ScenarioRouteRecord();
        record.setId(1L);
        record.setUserId(7L);
        record.setScenarioCode("PROJECT_FACT");
        record.setPrimaryConnectionId(primary);
        record.setBackupConnectionId(backup);
        record.setFailoverEnabled(failover);
        record.setVersion(1);
        record.setCreatedAt(Instant.now());
        record.setUpdatedAt(Instant.now());
        return record;
    }

    private ConnectionResponse connection(Long id, String name) {
        return new ConnectionResponse(id, 1L, name, "OPENAI_COMPATIBLE", "https://gw.example/v1",
                "/chat/completions", "CHAT_COMPLETIONS", "model-x", "****", true, 60000,
                "SUCCEEDED", 200, null, null, null, Instant.now(), Instant.now(),
                "NONE", false, false);
    }
}
