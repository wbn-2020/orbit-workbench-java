package com.orbitworkbench.aiconnection.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orbitworkbench.aiconnection.api.AiConnectionDtos.ConnectionResponse;
import com.orbitworkbench.aiconnection.api.AiScenarioDtos.ScenarioRouteResponse;
import com.orbitworkbench.aiconnection.api.AiScenarioDtos.UpsertRouteRequest;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiScenarioServiceTest {

    @Mock
    private AiScenarioMapper mapper;

    @Mock
    private AiConnectionService connectionService;

    @InjectMocks
    private AiScenarioService service;

    @Test
    void unconfiguredScenariosEchoTheConnectionTheyWouldActuallyUse() {
        when(mapper.listRoutes(7L)).thenReturn(List.of());
        when(connectionService.list(true, 1, 1)).thenReturn(
                new PageResult<>(List.of(connection(3L, "兜底账户", true)), 1, 1, 1));

        List<ScenarioRouteResponse> routes = service.list(7L);

        assertEquals(6, routes.size());
        assertEquals(List.of("INTERVIEW_QUESTION", "INTERVIEW_REPORT", "PROJECT_FACT",
                "KNOWLEDGE_ANSWER", "USER_FACT", "PROFILE_DIGEST"),
                routes.stream().map(ScenarioRouteResponse::scenario).toList());
        assertEquals("DEFAULT", routes.get(0).source());
        assertEquals(3L, routes.get(0).primaryConnectionId());
        assertEquals("兜底账户", routes.get(0).primaryConnectionName());
    }

    @Test
    void configuredScenariosReportRouteSourceAndVersions() {
        ScenarioRouteRecord record = new ScenarioRouteRecord();
        record.setUserId(7L);
        record.setScenarioCode("INTERVIEW_REPORT");
        record.setPrimaryConnectionId(5L);
        record.setBackupConnectionId(6L);
        record.setFailoverEnabled(true);
        record.setVersion(3);
        record.setUpdatedAt(Instant.now());
        when(mapper.listRoutes(7L)).thenReturn(List.of(record));
        when(connectionService.list(true, 1, 1)).thenReturn(new PageResult<>(List.of(), 1, 1, 0));

        List<ScenarioRouteResponse> routes = service.list(7L);

        ScenarioRouteResponse report = routes.stream()
                .filter(item -> item.scenario().equals("INTERVIEW_REPORT"))
                .findFirst().orElseThrow();
        assertEquals("ROUTE", report.source());
        assertEquals(true, report.failoverEnabled());
        assertEquals(3, report.version());
        ScenarioRouteResponse question = routes.stream()
                .filter(item -> item.scenario().equals("INTERVIEW_QUESTION"))
                .findFirst().orElseThrow();
        assertEquals("DEFAULT", question.source());
        assertEquals(null, question.primaryConnectionId());
    }

    @Test
    void upsertInsertsFirstRouteWithVersionOne() {
        when(mapper.findRoute(7L, "PROJECT_FACT"))
                .thenReturn(null, reread(5L, 6L, true, 1));
        when(connectionService.get(5L)).thenReturn(connection(5L, "主用", true));
        when(connectionService.get(6L)).thenReturn(connection(6L, "备用", true));

        ScenarioRouteResponse response = service.upsert(7L, AiScenario.PROJECT_FACT,
                new UpsertRouteRequest(5L, 6L, true));

        ArgumentCaptor<ScenarioRouteRecord> captor =
                ArgumentCaptor.forClass(ScenarioRouteRecord.class);
        verify(mapper).insertRoute(captor.capture());
        ScenarioRouteRecord saved = captor.getValue();
        assertEquals(7L, saved.getUserId());
        assertEquals("PROJECT_FACT", saved.getScenarioCode());
        assertEquals(5L, saved.getPrimaryConnectionId());
        assertEquals(6L, saved.getBackupConnectionId());
        assertEquals(true, saved.isFailoverEnabled());
        assertEquals(1, saved.getVersion());
        assertEquals(5L, response.primaryConnectionId());
        verify(mapper, never()).updateRoute(any());
    }

    @Test
    void upsertUpdatesExistingRouteInsteadOfInserting() {
        when(mapper.findRoute(7L, "PROJECT_FACT"))
                .thenReturn(existingRoute(), reread(5L, null, false, 3));
        when(connectionService.get(5L)).thenReturn(connection(5L, "主用", true));

        service.upsert(7L, AiScenario.PROJECT_FACT, new UpsertRouteRequest(5L, null, false));

        ArgumentCaptor<ScenarioRouteRecord> captor =
                ArgumentCaptor.forClass(ScenarioRouteRecord.class);
        verify(mapper).updateRoute(captor.capture());
        assertEquals(null, captor.getValue().getBackupConnectionId());
        assertEquals(false, captor.getValue().isFailoverEnabled());
        verify(mapper, never()).insertRoute(any());
    }

    @Test
    void upsertRejectsBackupSameAsPrimary() {
        when(connectionService.get(5L)).thenReturn(connection(5L, "主用", true));

        ApiException exception = assertThrows(ApiException.class, () -> service.upsert(
                7L, AiScenario.PROJECT_FACT, new UpsertRouteRequest(5L, 5L, true)));

        assertEquals(ErrorCode.VALIDATION_FAILED, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("备用账户不能与主用账户相同"));
        verify(mapper, never()).insertRoute(any());
    }

    @Test
    void upsertRejectsFailoverWithoutBackup() {
        when(connectionService.get(5L)).thenReturn(connection(5L, "主用", true));

        ApiException exception = assertThrows(ApiException.class, () -> service.upsert(
                7L, AiScenario.PROJECT_FACT, new UpsertRouteRequest(5L, null, true)));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertTrue(exception.getMessage().contains("备用账户"));
    }

    @Test
    void upsertRejectsDisabledConnection() {
        when(connectionService.get(5L)).thenReturn(connection(5L, "已停用账户", false));

        ApiException exception = assertThrows(ApiException.class, () -> service.upsert(
                7L, AiScenario.PROJECT_FACT, new UpsertRouteRequest(5L, null, false)));

        assertTrue(exception.getMessage().contains("未启用"),
                "应明确指出是哪个账户未启用，实际：" + exception.getMessage());
        verify(mapper, never()).insertRoute(any());
    }

    @Test
    void removingUnconfiguredRouteIsNotFound() {
        when(mapper.deleteRoute(7L, "KNOWLEDGE_ANSWER")).thenReturn(0);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.remove(7L, AiScenario.KNOWLEDGE_ANSWER));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }

    @Test
    void auditsFilterByScenarioWhenProvided() {
        when(mapper.listAudits(anyLong(), any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of());
        when(mapper.countAudits(7L, "INTERVIEW_REPORT")).thenReturn(0L);

        service.audits(7L, AiScenario.INTERVIEW_REPORT, 1, 20);

        verify(mapper).listAudits(7L, "INTERVIEW_REPORT", 20, 0);
        verify(mapper).countAudits(7L, "INTERVIEW_REPORT");
    }

    @Test
    void auditPageSizeIsClamped() {
        when(mapper.listAudits(any(), any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of());

        service.audits(7L, null, 0, 500);

        verify(mapper).listAudits(7L, null, 50, 0);
    }

    private ScenarioRouteRecord reread(Long primary, Long backup, boolean failover, int version) {
        ScenarioRouteRecord record = new ScenarioRouteRecord();
        record.setId(12L);
        record.setUserId(7L);
        record.setScenarioCode("PROJECT_FACT");
        record.setPrimaryConnectionId(primary);
        record.setPrimaryConnectionName("主用");
        record.setBackupConnectionId(backup);
        record.setBackupConnectionName(backup == null ? null : "备用");
        record.setFailoverEnabled(failover);
        record.setVersion(version);
        record.setUpdatedAt(Instant.now());
        return record;
    }

    private ScenarioRouteRecord existingRoute() {
        ScenarioRouteRecord record = new ScenarioRouteRecord();
        record.setId(12L);
        record.setUserId(7L);
        record.setScenarioCode("PROJECT_FACT");
        record.setPrimaryConnectionId(4L);
        record.setBackupConnectionId(6L);
        record.setFailoverEnabled(true);
        record.setVersion(2);
        record.setUpdatedAt(Instant.now());
        return record;
    }

    private ConnectionResponse connection(Long id, String name, boolean enabled) {
        return new ConnectionResponse(id, 1L, name, "OPENAI_COMPATIBLE", "https://gw.example/v1",
                "/chat/completions", "CHAT_COMPLETIONS", "model-x", "****", enabled, 60000,
                "SUCCEEDED", 200, null, null, null, Instant.now(), Instant.now(),
                "NONE", false, false, null, null, null);
    }
}
