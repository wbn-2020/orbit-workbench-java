package com.orbitworkbench.interview.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orbitworkbench.ai.application.AiInvocation;
import com.orbitworkbench.ai.application.AiStreamEvent;
import com.orbitworkbench.ai.application.ModelGateway;
import com.orbitworkbench.aiconnection.application.AiConnectionService;
import com.orbitworkbench.aiconnection.application.AiConnectionService.AiConnectionRuntimeConfig;
import com.orbitworkbench.interview.domain.InterviewReportRecord;
import com.orbitworkbench.interview.domain.InterviewSessionRecord;
import com.orbitworkbench.interview.domain.InterviewSessionStatus;
import com.orbitworkbench.interview.domain.InterviewTurnRecord;
import com.orbitworkbench.interview.domain.InterviewTurnType;
import com.orbitworkbench.interview.domain.ReportStatus;
import com.orbitworkbench.interview.infrastructure.mapper.InterviewReportMapper;
import com.orbitworkbench.interview.infrastructure.mapper.InterviewSessionMapper;
import com.orbitworkbench.interview.infrastructure.mapper.InterviewTurnMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InterviewReportServiceTest {

    private static final String VALID_JSON = """
            {"totalScore":84,"hiringRecommendation":"PASS",
             "dimensionScores":{"业务理解":85,"技术正确性":88,"原理理解":82,"实现深度":84,
               "项目实践能力":86,"问题分析":82,"方案完整性":83,"架构取舍":80,
               "排障与异常恢复":78,"表达结构":88,"边界意识":81},
             "strengths":["架构分层清晰"],
             "weaknesses":["降级方案模糊"],
             "followUpFindings":["对账细节不足"],
             "projectMastery":["极端并发边界"],
             "knowledgeGaps":["Redis Cluster 倾斜"],
             "studySuggestions":["补充一次降级压测数据","整理布隆过滤器参数推导笔记"]}
            """;

    @Mock
    private InterviewSessionMapper sessionMapper;

    @Mock
    private InterviewTurnMapper turnMapper;

    @Mock
    private InterviewReportMapper reportMapper;

    @Mock
    private AiConnectionService connectionService;

    @Mock
    private ModelGateway modelGateway;

    private InterviewReportService service;

    @BeforeEach
    void setUp() {
        service = new InterviewReportService(sessionMapper, turnMapper, reportMapper,
                connectionService, modelGateway, new ObjectMapper());
    }

    @Test
    void generateMarksReadyAndCompletesSessionOnValidOutput() {
        stubSessionAndPendingReport();
        stubModelOutput(VALID_JSON);
        InterviewReportRecord ready = report(ReportStatus.REPORT_READY);
        ready.setTotalScore(84);
        when(reportMapper.findBySessionId(21L))
                .thenReturn(report(ReportStatus.REPORT_PENDING))
                .thenReturn(ready);
        when(reportMapper.markReady(eq(21L), eq(84), any(), eq("PASS"),
                any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(sessionMapper.updateStatus(eq(21L), eq(InterviewSessionStatus.COMPLETING),
                eq(InterviewSessionStatus.COMPLETED), isNull(), isNull(), any())).thenReturn(1);

        var response = service.generate(7L, 21L, 5L);

        assertEquals(ReportStatus.REPORT_READY.name(), response.report().status());
        assertEquals(84, response.report().totalScore());
        verify(reportMapper).markReady(eq(21L), eq(84), any(), eq("PASS"),
                any(), any(), any(), any(), any(), any(), any(), any());
        verify(sessionMapper).updateStatus(eq(21L), eq(InterviewSessionStatus.COMPLETING),
                eq(InterviewSessionStatus.COMPLETED), isNull(), isNull(), any());
    }

    @Test
    void generateMarksFailedOnInvalidStructuredOutput() {
        stubSessionAndPendingReport();
        stubModelOutput("抱歉，我无法按该格式回答。");

        assertThrows(ApiException.class, () -> service.generate(7L, 21L, 5L));

        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(reportMapper).markFailed(eq(21L), reason.capture(), any());
        org.junit.jupiter.api.Assertions.assertTrue(
                reason.getValue() != null && reason.getValue().contains("JSON"),
                "失败摘要应说明 JSON 结构问题，实际：" + reason.getValue());
        verify(reportMapper, never()).markReady(anyLong(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void generateRejectsWhenReportAlreadyReady() {
        InterviewSessionRecord session = session(InterviewSessionStatus.COMPLETING);
        when(sessionMapper.findById(21L)).thenReturn(session);
        when(reportMapper.findBySessionId(21L)).thenReturn(report(ReportStatus.REPORT_READY));

        assertThrows(ApiException.class, () -> service.generate(7L, 21L, 5L));
        verify(modelGateway, never()).stream(any(AiInvocation.class));
    }

    @Test
    void retryMovesFailedToPendingThenGenerates() {
        InterviewSessionRecord session = session(InterviewSessionStatus.COMPLETING);
        when(sessionMapper.findById(21L)).thenReturn(session);
        when(reportMapper.findBySessionId(21L))
                .thenReturn(report(ReportStatus.REPORT_FAILED))
                .thenReturn(report(ReportStatus.REPORT_PENDING));
        when(reportMapper.markPending(eq(21L), any())).thenReturn(1);
        when(connectionService.list(true, 1, 1)).thenReturn(new com.orbitworkbench.shared.api.PageResult<>(
                java.util.List.of(new com.orbitworkbench.aiconnection.api.AiConnectionDtos.ConnectionResponse(
                        5L, 1L, "主账户", "OPENAI_COMPATIBLE", "https://gw.example/v1",
                        "/chat/completions", "CHAT_COMPLETIONS", "gpt-4o",
                        "sk-****", true, 60000, "SUCCEEDED", 200, null, null, null,
                        null, null)),
                1, 1, 1));
        stubModelOutput(VALID_JSON);
        when(reportMapper.markReady(eq(21L), eq(84), any(), eq("PASS"),
                any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        when(sessionMapper.updateStatus(eq(21L), eq(InterviewSessionStatus.COMPLETING),
                eq(InterviewSessionStatus.COMPLETED), isNull(), isNull(), any())).thenReturn(1);

        service.retry(7L, 21L);

        verify(reportMapper).markPending(eq(21L), any());
        verify(reportMapper).markReady(eq(21L), eq(84), any(), eq("PASS"),
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void readyStudySuggestionsParsesSuggestionsJson() {
        InterviewSessionRecord session = session(InterviewSessionStatus.COMPLETED);
        InterviewReportRecord report = report(ReportStatus.REPORT_READY);
        report.setStudySuggestionsJson("[\"任务A\",\"任务B\"]");
        when(sessionMapper.findById(21L)).thenReturn(session);
        when(reportMapper.findBySessionId(21L)).thenReturn(report);

        List<String> suggestions = service.readyStudySuggestions(7L, 21L);

        assertEquals(List.of("任务A", "任务B"), suggestions);
    }

    private void stubSessionAndPendingReport() {
        InterviewSessionRecord session = session(InterviewSessionStatus.COMPLETING);
        when(sessionMapper.findById(21L)).thenReturn(session);
        when(reportMapper.findBySessionId(21L)).thenReturn(report(ReportStatus.REPORT_PENDING));
        InterviewTurnRecord turn = new InterviewTurnRecord();
        turn.setTurnNo(1);
        turn.setTurnType(InterviewTurnType.MAIN);
        turn.setQuestion("介绍秒杀架构");
        turn.setAnswer("Redis 预扣 + 异步落库");
        turn.setAnswerSource(com.orbitworkbench.interview.domain.AnswerSource.INDEPENDENT);
        when(turnMapper.listBySession(21L)).thenReturn(List.of(turn));
        when(connectionService.getRuntimeConfig(5L)).thenReturn(new AiConnectionRuntimeConfig(
                5L, 9L, "主账户", "https://gw.example/v1", "/chat/completions",
                "CHAT_COMPLETIONS", "gpt-4o", "sk-test", 60000));
    }

    private void stubModelOutput(String text) {
        when(modelGateway.stream(any(AiInvocation.class)))
                .thenReturn(Flux.just(new AiStreamEvent(
                        "delta", text, null, null, null, false, null, null, null)));
    }

    private InterviewSessionRecord session(InterviewSessionStatus status) {
        InterviewSessionRecord record = new InterviewSessionRecord();
        record.setId(21L);
        record.setUserId(7L);
        record.setTitle("字节跳动 · 技术面");
        record.setTopicMode("PROJECT_DEEP_DIVE");
        record.setForm("TRAINING");
        record.setRound("FIRST");
        record.setQuestionLimit(4);
        record.setFollowUpLimit(3);
        record.setTurnLimit(12);
        record.setDurationLimitMinutes(45);
        record.setStatus(status);
        return record;
    }

    private InterviewReportRecord report(ReportStatus status) {
        InterviewReportRecord record = new InterviewReportRecord();
        record.setId(33L);
        record.setSessionId(21L);
        record.setStatus(status);
        record.setRetryCount(0);
        record.setCreatedAt(Instant.now());
        record.setUpdatedAt(Instant.now());
        return record;
    }
}
