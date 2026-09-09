package com.orbitworkbench.interview.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orbitworkbench.ai.application.AiInvocation;
import com.orbitworkbench.ai.application.AiProviderException;
import com.orbitworkbench.ai.application.AiStreamEvent;
import com.orbitworkbench.ai.application.ModelGateway;
import com.orbitworkbench.aiconnection.application.AiCallAuditRecorder;
import com.orbitworkbench.aiconnection.application.AiConnectionService.AiConnectionRuntimeConfig;
import com.orbitworkbench.aiconnection.application.AiScenarioExecutionService;
import com.orbitworkbench.aiconnection.application.AiScenarioRouter;
import com.orbitworkbench.aiconnection.domain.AiScenario;
import com.orbitworkbench.interview.api.InterviewDtos.NextQuestionRequest;
import com.orbitworkbench.interview.domain.InterviewSessionRecord;
import com.orbitworkbench.interview.domain.InterviewSessionStatus;
import com.orbitworkbench.interview.domain.InterviewTurnRecord;
import com.orbitworkbench.interview.domain.InterviewTurnType;
import com.orbitworkbench.interview.infrastructure.mapper.InterviewSessionMapper;
import com.orbitworkbench.interview.infrastructure.mapper.InterviewTurnMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import java.time.Duration;
import java.util.List;
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
class InterviewQuestionServiceTest {

    private static final AiConnectionRuntimeConfig PRIMARY = new AiConnectionRuntimeConfig(
            5L, 9L, "主账户", "https://gw.example/v1", "/chat/completions",
            "CHAT_COMPLETIONS", "Deepseek-v4-flash", "sk-test", 60000);

    @Mock
    private InterviewSessionMapper sessionMapper;

    @Mock
    private InterviewTurnMapper turnMapper;

    @Mock
    private AiScenarioRouter aiScenarioRouter;

    @Mock
    private AiScenarioExecutionService aiScenarioExecution;

    @Mock
    private AiCallAuditRecorder aiCallAuditRecorder;

    @Mock
    private ModelGateway modelGateway;

    private InterviewQuestionService service;

    @BeforeEach
    void setUp() {
        service = new InterviewQuestionService(sessionMapper, turnMapper, aiScenarioRouter,
                aiScenarioExecution, aiCallAuditRecorder, modelGateway,
                new com.fasterxml.jackson.databind.ObjectMapper());
        when(aiScenarioRouter.resolve(any(), any(), any())).thenReturn(
                new AiScenarioRouter.ResolvedRoute(PRIMARY, null, false, AiScenarioRouter.SOURCE_PINNED));
        when(aiCallAuditRecorder.start(any(), any(), any(), anyInt(), any())).thenReturn(77L);
        when(aiCallAuditRecorder.snapshotJson(any(), any(), anyInt(), anyBoolean())).thenReturn("{}");
    }

    @Test
    void promptCarriesInterviewerPersonaAndProjectSnapshot() {
        InterviewSessionRecord session = runningSession();
        session.setTopicMode("PROJECT_DEEP_DIVE");
        session.setInterviewerSnapshotJson(
                "{\"systemPrompt\":\"严格追问真实职责\",\"focusTags\":[\"架构取舍\"]}");
        session.setProjectBindingsJson(
                "[{\"projectName\":\"秒杀中台\",\"versionNumber\":2,\"facts\":"
                        + "[{\"title\":\"高并发链路\",\"content\":\"秒杀核心链路设计\"}]}]");
        when(sessionMapper.findById(21L)).thenReturn(session);
        when(turnMapper.countBySession(21L)).thenReturn(0);
        when(turnMapper.listBySession(21L)).thenReturn(List.of());
        stubExecution("请介绍秒杀核心链路中你亲自负责的部分，以及压测数据？");
        stubInsertId(101L);
        when(turnMapper.findById(101L)).thenReturn(savedTurn(101L, InterviewTurnType.MAIN,
                "请介绍秒杀核心链路中你亲自负责的部分，以及压测数据？"));

        service.next(7L, 21L, new NextQuestionRequest("MAIN", null));

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        verify(aiScenarioExecution).executeText(eq(AiScenario.INTERVIEW_QUESTION), eq(7L), eq(5L),
                system.capture(), user.capture(), anyInt(), any(Duration.class));
        assertEquals(true, system.getValue().contains("严格追问真实职责"));
        assertEquals(true, system.getValue().contains("重点考查方向：架构取舍"));
        assertEquals(true, user.getValue().contains("秒杀中台"));
        assertEquals(true, user.getValue().contains("高并发链路：秒杀核心链路设计"));
    }

    @Test
    void promptCarriesKnowledgeCardsWhenSnapshotPresent() {
        InterviewSessionRecord session = runningSession();
        session.setKnowledgeBindingsJson(
                "[{\"cardId\":77,\"title\":\"库存分桶方案\",\"summary\":\"库存扣减按桶拆分避免热点\","
                        + "\"tags\":\"[\\\"库存\\\"]\",\"distilledAt\":\"2026-09-08T00:00:00Z\"}]");
        when(sessionMapper.findById(21L)).thenReturn(session);
        when(turnMapper.countBySession(21L)).thenReturn(0);
        when(turnMapper.listBySession(21L)).thenReturn(List.of());
        stubExecution("你实践过的库存分桶是怎么设计的？");
        stubInsertId(102L);
        when(turnMapper.findById(102L)).thenReturn(savedTurn(102L, InterviewTurnType.MAIN,
                "你实践过的库存分桶是怎么设计的？"));

        service.next(7L, 21L, new NextQuestionRequest("MAIN", null));

        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        verify(aiScenarioExecution).executeText(eq(AiScenario.INTERVIEW_QUESTION), eq(7L), eq(5L),
                any(), user.capture(), anyInt(), any(Duration.class));
        assertEquals(true, user.getValue().contains("个人经验（知识卡片）"));
        assertEquals(true, user.getValue().contains("库存分桶方案：库存扣减按桶拆分避免热点"));
    }

    @Test
    void promptOmitsKnowledgeSectionWhenSnapshotAbsent() {
        InterviewSessionRecord session = runningSession();
        when(sessionMapper.findById(21L)).thenReturn(session);
        when(turnMapper.countBySession(21L)).thenReturn(0);
        when(turnMapper.listBySession(21L)).thenReturn(List.of());
        stubExecution("请介绍 HashMap？");
        stubInsertId(103L);
        when(turnMapper.findById(103L)).thenReturn(savedTurn(103L, InterviewTurnType.MAIN,
                "请介绍 HashMap？"));

        service.next(7L, 21L, new NextQuestionRequest("MAIN", null));

        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        verify(aiScenarioExecution).executeText(eq(AiScenario.INTERVIEW_QUESTION), eq(7L), eq(5L),
                any(), user.capture(), anyInt(), any(Duration.class));
        assertEquals(false, user.getValue().contains("个人经验（知识卡片）"));
    }

    @Test
    void nextGeneratesMainQuestionAndPersistsTurn() {
        InterviewSessionRecord session = runningSession();
        when(sessionMapper.findById(21L)).thenReturn(session);
        when(turnMapper.countBySession(21L)).thenReturn(0);
        when(turnMapper.listBySession(21L)).thenReturn(List.of());
        String question = "请解释 ConcurrentHashMap 在 JDK 8 中如何保证线程安全，以及 size() 的实现思路？";
        stubExecution("追问：" + question);
        stubInsertId(100L);
        when(turnMapper.findById(100L)).thenReturn(savedTurn(100L, InterviewTurnType.MAIN, question));

        var response = service.next(7L, 21L, new NextQuestionRequest("MAIN", null));

        ArgumentCaptor<InterviewTurnRecord> captor =
                ArgumentCaptor.forClass(InterviewTurnRecord.class);
        verify(turnMapper).insert(captor.capture());
        assertEquals(1, captor.getValue().getTurnNo());
        assertEquals(InterviewTurnType.MAIN, captor.getValue().getTurnType());
        // 阻塞出题必须沿用与 SSE 相同的清洗规则，落库题目不带"追问："前缀
        assertEquals(question, captor.getValue().getQuestion());
        assertEquals(100L, response.id());
    }

    @Test
    void nextFollowUpRequiresLastAnsweredTurn() {
        InterviewSessionRecord session = runningSession();
        when(sessionMapper.findById(21L)).thenReturn(session);
        InterviewTurnRecord unanswered = turn(InterviewTurnType.MAIN, "问题");
        unanswered.setAnswer(null);
        when(turnMapper.listBySession(21L)).thenReturn(List.of(unanswered));

        assertThrows(ApiException.class,
                () -> service.next(7L, 21L, new NextQuestionRequest("FOLLOW_UP", null)));
        verify(turnMapper, never()).insert(any(InterviewTurnRecord.class));
    }

    @Test
    void nextRejectsWhenTurnBudgetExhausted() {
        InterviewSessionRecord session = runningSession();
        session.setTurnLimit(2);
        when(sessionMapper.findById(21L)).thenReturn(session);
        when(turnMapper.countBySession(21L)).thenReturn(2);

        assertThrows(ApiException.class,
                () -> service.next(7L, 21L, new NextQuestionRequest("MAIN", null)));
    }

    @Test
    void nextRejectsWhenPaused() {
        InterviewSessionRecord session = runningSession();
        session.setStatus(InterviewSessionStatus.PAUSED);
        when(sessionMapper.findById(21L)).thenReturn(session);

        assertThrows(ApiException.class,
                () -> service.next(7L, 21L, new NextQuestionRequest("MAIN", null)));
    }

    @Test
    void nextFailsOnTooShortModelOutput() {
        InterviewSessionRecord session = runningSession();
        when(sessionMapper.findById(21L)).thenReturn(session);
        when(turnMapper.countBySession(21L)).thenReturn(0);
        when(turnMapper.listBySession(21L)).thenReturn(List.of());
        stubExecution("好的。");

        assertThrows(ApiException.class,
                () -> service.next(7L, 21L, new NextQuestionRequest("MAIN", null)));
        verify(turnMapper, never()).insert(any(InterviewTurnRecord.class));
    }

    @Test
    void anotherUsersSessionIsRejected() {
        InterviewSessionRecord session = runningSession();
        session.setUserId(8L);
        when(sessionMapper.findById(21L)).thenReturn(session);

        assertThrows(ApiException.class,
                () -> service.next(7L, 21L, new NextQuestionRequest("MAIN", null)));
    }

    @Test
    void streamNextUsesScenarioRouteAndToleratesNullTextGatewayEvents() {
        InterviewSessionRecord session = runningSession();
        when(sessionMapper.findById(21L)).thenReturn(session);
        when(turnMapper.countBySession(21L)).thenReturn(0);
        when(turnMapper.listBySession(21L)).thenReturn(List.of());
        // 真实网关会在文本增量之间夹带 text=null 的 start/finish 事件
        when(modelGateway.stream(any(AiInvocation.class))).thenReturn(Flux.just(
                new AiStreamEvent("start", null, null, null, null, false, null, null, null),
                new AiStreamEvent("delta", "请说明", null, null, null, false, null, null, null),
                new AiStreamEvent("delta", null, null, null, null, false, null, null, null),
                new AiStreamEvent("delta", "线程池参数？", null, null, null, false, null, null, null),
                new AiStreamEvent("finish", null, null, null, null, true, null, null, null)));
        stubInsertId(140L);

        List<org.springframework.http.codec.ServerSentEvent<String>> events =
                service.streamNext(7L, 21L, new NextQuestionRequest("MAIN", null))
                        .collectList().block();

        assertEquals(true, events != null && events.size() >= 3);
        assertEquals("start", events.get(0).event());
        assertEquals("done", events.get(events.size() - 1).event());
        long deltas = events.stream().filter(e -> "delta".equals(e.event())).count();
        assertEquals(2, deltas);
        ArgumentCaptor<InterviewTurnRecord> captor =
                ArgumentCaptor.forClass(InterviewTurnRecord.class);
        verify(turnMapper).insert(captor.capture());
        assertEquals("请说明线程池参数？", captor.getValue().getQuestion());
        // 流式路径同样按场景 + 会话快照账户选择，但不做备用切换
        verify(aiScenarioRouter).resolve(7L, AiScenario.INTERVIEW_QUESTION, 5L);
        ArgumentCaptor<AiInvocation> invocation = ArgumentCaptor.forClass(AiInvocation.class);
        verify(modelGateway).stream(invocation.capture());
        assertEquals(5L, invocation.getValue().connection().connectionId());
    }

    @Test
    void streamNextEmitsErrorEventAndNeverPersistsHalfQuestion() {
        InterviewSessionRecord session = runningSession();
        when(sessionMapper.findById(21L)).thenReturn(session);
        when(turnMapper.countBySession(21L)).thenReturn(0);
        when(turnMapper.listBySession(21L)).thenReturn(List.of());
        when(modelGateway.stream(any(AiInvocation.class))).thenReturn(Flux.concat(
                Flux.just(new AiStreamEvent(
                        "delta", "请说明", null, null, null, false, null, null, null)),
                Flux.error(new AiProviderException(ErrorCode.RATE_LIMITED,
                        HttpStatus.TOO_MANY_REQUESTS, 429, "上游限流", null))));

        List<org.springframework.http.codec.ServerSentEvent<String>> events =
                service.streamNext(7L, 21L, new NextQuestionRequest("MAIN", null))
                        .collectList().block();

        assertEquals("error", events.get(events.size() - 1).event());
        assertEquals(true, events.get(events.size() - 1).data().contains("RATE_LIMITED"),
                "error 事件应带上游错误码，实际：" + events.get(events.size() - 1).data());
        verify(turnMapper, never()).insert(any(InterviewTurnRecord.class));
        verify(aiCallAuditRecorder).finish(eq(77L), eq(7L), eq(AiScenario.INTERVIEW_QUESTION),
                eq(AiCallAuditRecorder.STATUS_FAILED), eq("RATE_LIMITED"), anyInt(), anyInt(),
                anyInt(), eq(5L), eq(false));
    }

    @Test
    void streamNextRecordsSucceededAuditWithQuestionLength() {
        InterviewSessionRecord session = runningSession();
        when(sessionMapper.findById(21L)).thenReturn(session);
        when(turnMapper.countBySession(21L)).thenReturn(0);
        when(turnMapper.listBySession(21L)).thenReturn(List.of());
        when(modelGateway.stream(any(AiInvocation.class))).thenReturn(Flux.just(
                new AiStreamEvent("delta", "请说明线程池的核心参数与拒绝策略？",
                        null, null, null, false, null, null, null)));
        stubInsertId(141L);

        service.streamNext(7L, 21L, new NextQuestionRequest("MAIN", null)).collectList().block();

        verify(aiCallAuditRecorder).finish(eq(77L), eq(7L), eq(AiScenario.INTERVIEW_QUESTION),
                eq(AiCallAuditRecorder.STATUS_SUCCEEDED), isNull(), anyInt(), anyInt(),
                eq("请说明线程池的核心参数与拒绝策略？".length()), eq(5L), eq(false));
    }

    @Test
    void streamNextRejectsTooShortOutputWithoutInsert() {
        InterviewSessionRecord session = runningSession();
        when(sessionMapper.findById(21L)).thenReturn(session);
        when(turnMapper.countBySession(21L)).thenReturn(0);
        when(turnMapper.listBySession(21L)).thenReturn(List.of());
        when(modelGateway.stream(any(AiInvocation.class))).thenReturn(Flux.just(
                new AiStreamEvent("delta", "好的", null, null, null, false, null, null, null)));

        List<org.springframework.http.codec.ServerSentEvent<String>> events =
                service.streamNext(7L, 21L, new NextQuestionRequest("MAIN", null))
                        .collectList().block();

        assertEquals("error", events.get(events.size() - 1).event());
        verify(turnMapper, never()).insert(any(InterviewTurnRecord.class));
        verify(aiCallAuditRecorder).finish(eq(77L), eq(7L), eq(AiScenario.INTERVIEW_QUESTION),
                eq(AiCallAuditRecorder.STATUS_FAILED), eq("INVALID_STRUCTURED_OUTPUT"),
                anyInt(), anyInt(), anyInt(), eq(5L), eq(false));
    }

    private void stubExecution(String text) {
        when(aiScenarioExecution.executeText(any(), any(), any(), any(), any(), anyInt(),
                any(Duration.class))).thenReturn(text);
    }

    private void stubInsertId(Long id) {
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.<InterviewTurnRecord>getArgument(0).setId(id);
            return null;
        }).when(turnMapper).insert(any(InterviewTurnRecord.class));
    }

    @Test
    void nextRejectsUnknownTurnTypeAsBadRequestWithoutCallingModel() {
        when(sessionMapper.findById(21L)).thenReturn(runningSession());

        ApiException exception = assertThrows(ApiException.class,
                () -> service.next(7L, 21L, new NextQuestionRequest("BOTH", null)));

        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals(ErrorCode.INVALID_REQUEST, exception.getErrorCode());
        assertEquals("turnType 取值不合法", exception.getMessage());
        verify(aiScenarioExecution, never()).executeText(any(), any(), any(), any(), any(),
                anyInt(), any());
        verify(turnMapper, never()).insert(any(InterviewTurnRecord.class));
    }

    @Test
    void streamNextRejectsUnknownTurnTypeAsBadRequest() {
        when(sessionMapper.findById(21L)).thenReturn(runningSession());

        assertThrows(ApiException.class,
                () -> service.streamNext(7L, 21L, new NextQuestionRequest("main", null)));

        verify(modelGateway, never()).stream(any());
    }

    private InterviewTurnRecord savedTurn(Long id, InterviewTurnType type, String question) {
        InterviewTurnRecord record = turn(type, question);
        record.setId(id);
        return record;
    }

    private InterviewSessionRecord runningSession() {
        InterviewSessionRecord record = new InterviewSessionRecord();
        record.setId(21L);
        record.setUserId(7L);
        record.setTitle("字节跳动 · 技术面");
        record.setTopicMode("ROTE");
        record.setForm("TRAINING");
        record.setRound("FIRST");
        record.setInterviewerNameSnapshot("Java 基础训练官");
        record.setTargetRole("Java 后端工程师");
        record.setAiConnectionIdSnapshot(5L);
        record.setQuestionLimit(5);
        record.setFollowUpLimit(3);
        record.setTurnLimit(12);
        record.setDurationLimitMinutes(45);
        record.setStatus(InterviewSessionStatus.RUNNING);
        return record;
    }

    private InterviewTurnRecord turn(InterviewTurnType type, String question) {
        InterviewTurnRecord record = new InterviewTurnRecord();
        record.setId(9L);
        record.setSessionId(21L);
        record.setTurnNo(1);
        record.setTurnType(type);
        record.setQuestion(question);
        record.setAnswer("已回答内容");
        return record;
    }
}
