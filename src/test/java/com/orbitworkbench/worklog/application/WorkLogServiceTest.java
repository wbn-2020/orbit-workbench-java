package com.orbitworkbench.worklog.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orbitworkbench.aiconnection.application.AiScenarioExecutionService;
import com.orbitworkbench.ai.application.RequestRejectedException;
import com.orbitworkbench.aiconnection.domain.AiScenario;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.worklog.api.WorkLogDtos.CreateWorkLogRequest;
import com.orbitworkbench.worklog.domain.KnowledgeCardRow;
import com.orbitworkbench.worklog.domain.WorkLogCategory;
import com.orbitworkbench.worklog.domain.WorkLogRow;
import com.orbitworkbench.worklog.infrastructure.mapper.KnowledgeCardMapper;
import com.orbitworkbench.worklog.infrastructure.mapper.WorkLogMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkLogServiceTest {

    @Mock
    private WorkLogMapper workLogMapper;
    @Mock
    private KnowledgeCardMapper knowledgeCardMapper;
    @Mock
    private AiScenarioExecutionService executionService;
    @Mock
    private WorkLogDistillationWriteService writeService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private WorkLogRow logRow(long id, WorkLogCategory category) {
        WorkLogRow row = new WorkLogRow();
        row.setId(id);
        row.setTitle("排查订单超时");
        row.setContent("高峰期 2s 延迟，根因是连接池被报表占满");
        row.setCategory(category);
        row.setDistilled(false);
        row.setCreatedAt(Instant.parse("2026-09-07T00:00:00Z"));
        return row;
    }

    private KnowledgeCardRow cardRow() {
        KnowledgeCardRow row = new KnowledgeCardRow();
        row.setId(77L);
        row.setTitle("排查订单超时");
        row.setSummary("报表查询应迁移到只读副本");
        row.setSourceLogId(3L);
        row.setTagsJson("[\"数据库\"]");
        row.setCreatedAt(Instant.parse("2026-09-07T00:00:00Z"));
        return row;
    }

    @Test
    void createRejectsInvalidCategoryAndBlankContent() {
        WorkLogService service = new WorkLogService(workLogMapper, knowledgeCardMapper,
                executionService, objectMapper, writeService);

        assertThrows(ApiException.class, () -> service.create(1L,
                new CreateWorkLogRequest("标题", "内容", "gossip")));
        assertThrows(ApiException.class, () -> service.create(1L,
                new CreateWorkLogRequest("标题", "   ", "project")));
        verify(workLogMapper, org.mockito.Mockito.never()).insert(any());
    }

    @Test
    void distillReplaysExistingCardWithoutCallingAi() {
        WorkLogService service = new WorkLogService(workLogMapper, knowledgeCardMapper,
                executionService, objectMapper, writeService);
        when(workLogMapper.findOwned(1L, 3L)).thenReturn(logRow(3L, WorkLogCategory.PROJECT));
        when(knowledgeCardMapper.findBySourceLog(1L, 3L)).thenReturn(cardRow());

        var response = service.distill(1L, 3L);

        assertEquals("77", response.id());
        verify(executionService, org.mockito.Mockito.never()).executeText(any(), any(), any(),
                any(), any(), anyInt(), any());
    }

    @Test
    void distillParsesFencedJsonWithSurroundingProse() {
        WorkLogService service = new WorkLogService(workLogMapper, knowledgeCardMapper,
                executionService, objectMapper, writeService);
        when(workLogMapper.findOwned(1L, 3L)).thenReturn(logRow(3L, WorkLogCategory.INCIDENT));
        when(knowledgeCardMapper.findBySourceLog(1L, 3L)).thenReturn(null, cardRow());
        String dirty = "好的，以下是蒸馏结果：\n```json\n{\"summary\":\"报表查询迁移只读副本\",\n"
                + "\"tags\":[\"数据库\",\"报表\"]\n}\n```\n希望对你有帮助。";
        when(executionService.executeText(eq(AiScenario.PROJECT_FACT), eq(1L), eq(null),
                any(), any(), anyInt(), any())).thenReturn(dirty);

        var response = service.distill(1L, 3L);

        // 响应来自写后重读（cardRow）；关键断言是脏输出被正确解析后交给写侧落库
        assertEquals("77", response.id());
        verify(writeService).persist(eq(1L), eq(3L), eq("排查订单超时"),
                eq("报表查询迁移只读副本"), eq(java.util.List.of("数据库", "报表")));
    }

    @Test
    void distillFallsBackToRawTextWhenAiReturnsGarbage() {
        WorkLogService service = new WorkLogService(workLogMapper, knowledgeCardMapper,
                executionService, objectMapper, writeService);
        when(workLogMapper.findOwned(1L, 3L)).thenReturn(logRow(3L, WorkLogCategory.DECISION));
        when(knowledgeCardMapper.findBySourceLog(1L, 3L)).thenReturn(null, cardRow());
        when(executionService.executeText(eq(AiScenario.PROJECT_FACT), eq(1L), eq(null),
                any(), any(), anyInt(), any())).thenReturn("这不是一段 JSON，只是普通结论。");

        service.distill(1L, 3L);

        verify(writeService).persist(eq(1L), eq(3L), eq("排查订单超时"),
                eq("这不是一段 JSON，只是普通结论。"), eq(java.util.List.of("DECISION")));
    }

    @Test
    void distillReplaysExistingCardEvenWhenAiScenarioRouteRejectsRequest() {
        WorkLogService service = new WorkLogService(workLogMapper, knowledgeCardMapper,
                executionService, objectMapper, writeService);
        when(workLogMapper.findOwned(1L, 3L)).thenReturn(logRow(3L, WorkLogCategory.PROJECT));
        when(knowledgeCardMapper.findBySourceLog(1L, 3L)).thenReturn(null);
        when(executionService.executeText(eq(AiScenario.PROJECT_FACT), eq(1L), eq(null),
                any(), any(), anyInt(), any()))
                .thenThrow(new RequestRejectedException(
                        org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                        ErrorCode.UNSUPPORTED_CAPABILITY, "当前连接不支持联网检索"));

        ApiException exception = assertThrows(ApiException.class, () -> service.distill(1L, 3L));

        assertEquals(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, exception.getStatus());
        assertEquals(ErrorCode.UNSUPPORTED_CAPABILITY, exception.getErrorCode());
        verify(writeService, org.mockito.Mockito.never()).persist(any(), any(), any(), any(), any());
    }

    @Test
    void distillReturns500WhenCardMissingAfterWrite() {
        WorkLogService service = new WorkLogService(workLogMapper, knowledgeCardMapper,
                executionService, objectMapper, writeService);
        when(workLogMapper.findOwned(1L, 3L)).thenReturn(logRow(3L, WorkLogCategory.PROJECT));
        when(knowledgeCardMapper.findBySourceLog(1L, 3L)).thenReturn(null, null);
        when(executionService.executeText(eq(AiScenario.PROJECT_FACT), eq(1L), eq(null),
                any(), any(), anyInt(), any())).thenReturn("{\"summary\":\"s\",\"tags\":[]}");
        lenient().when(writeService.persist(any(), any(), any(), any(), any())).thenReturn(null);

        ApiException exception = assertThrows(ApiException.class, () -> service.distill(1L, 3L));

        assertEquals(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, exception.getStatus());
    }
}
