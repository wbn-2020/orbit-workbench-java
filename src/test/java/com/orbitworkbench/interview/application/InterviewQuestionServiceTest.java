package com.orbitworkbench.interview.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orbitworkbench.ai.application.AiInvocation;
import com.orbitworkbench.ai.application.AiStreamEvent;
import com.orbitworkbench.ai.application.ModelGateway;
import com.orbitworkbench.aiconnection.application.AiConnectionService;
import com.orbitworkbench.aiconnection.application.AiConnectionService.AiConnectionRuntimeConfig;
import com.orbitworkbench.interview.api.InterviewDtos.NextQuestionRequest;
import com.orbitworkbench.interview.domain.InterviewSessionRecord;
import com.orbitworkbench.interview.domain.InterviewSessionStatus;
import com.orbitworkbench.interview.domain.InterviewTurnRecord;
import com.orbitworkbench.interview.domain.InterviewTurnType;
import com.orbitworkbench.interview.infrastructure.mapper.InterviewSessionMapper;
import com.orbitworkbench.interview.infrastructure.mapper.InterviewTurnMapper;
import com.orbitworkbench.shared.api.ApiException;
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
class InterviewQuestionServiceTest {

    @Mock
    private InterviewSessionMapper sessionMapper;

    @Mock
    private InterviewTurnMapper turnMapper;

    @Mock
    private AiConnectionService aiConnectionService;

    @Mock
    private ModelGateway modelGateway;

    private InterviewQuestionService service;

    @BeforeEach
    void setUp() {
        service = new InterviewQuestionService(sessionMapper, turnMapper,
                aiConnectionService, modelGateway,
                new com.fasterxml.jackson.databind.ObjectMapper());
        when(aiConnectionService.getRuntimeConfig(5L)).thenReturn(new AiConnectionRuntimeConfig(
                5L, 9L, "主账户", "https://gw.example/v1", "/chat/completions",
                "CHAT_COMPLETIONS", "Deepseek-v4-flash", "sk-test", 60000));
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
        stubModelOutput("请介绍秒杀核心链路中你亲自负责的部分，以及压测数据？");
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.<InterviewTurnRecord>getArgument(0).setId(101L);
            return null;
        }).when(turnMapper).insert(any(InterviewTurnRecord.class));
        InterviewTurnRecord saved = turn(InterviewTurnType.MAIN,
                "请介绍秒杀核心链路中你亲自负责的部分，以及压测数据？");
        saved.setId(101L);
        when(turnMapper.findById(101L)).thenReturn(saved);

        service.next(7L, 21L, new NextQuestionRequest("MAIN", null));

        ArgumentCaptor<AiInvocation> captor = ArgumentCaptor.forClass(AiInvocation.class);
        verify(modelGateway).stream(captor.capture());
        String system = captor.getValue().systemPrompt();
        String user = captor.getValue().userPrompt();
        assertEquals(true, system.contains("严格追问真实职责"));
        assertEquals(true, system.contains("重点考查方向：架构取舍"));
        assertEquals(true, user.contains("秒杀中台"));
        assertEquals(true, user.contains("高并发链路：秒杀核心链路设计"));
    }

    @Test
    void nextGeneratesMainQuestionAndPersistsTurn() {
        InterviewSessionRecord session = runningSession();
        when(sessionMapper.findById(21L)).thenReturn(session);
        when(turnMapper.countBySession(21L)).thenReturn(0);
        when(turnMapper.listBySession(21L)).thenReturn(List.of());
        stubModelOutput("请解释 ConcurrentHashMap 在 JDK 8 中如何保证线程安全，以及 size() 的实现思路？");
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.<InterviewTurnRecord>getArgument(0).setId(100L);
            return null;
        }).when(turnMapper).insert(any(InterviewTurnRecord.class));
        InterviewTurnRecord saved = turn(InterviewTurnType.MAIN,
                "请解释 ConcurrentHashMap 在 JDK 8 中如何保证线程安全，以及 size() 的实现思路？");
        saved.setId(100L);
        when(turnMapper.findById(100L)).thenReturn(saved);

        var response = service.next(7L, 21L,
                new NextQuestionRequest("MAIN", null));

        ArgumentCaptor<InterviewTurnRecord> captor =
                ArgumentCaptor.forClass(InterviewTurnRecord.class);
        verify(turnMapper).insert(captor.capture());
        assertEquals(1, captor.getValue().getTurnNo());
        assertEquals(InterviewTurnType.MAIN, captor.getValue().getTurnType());
        assertEquals("请解释 ConcurrentHashMap 在 JDK 8 中如何保证线程安全，以及 size() 的实现思路？",
                captor.getValue().getQuestion());
        assertEquals(100L, response.id());
    }

    @Test
    void nextFollowUpRequiresLastAnsweredTurn() {
        InterviewSessionRecord session = runningSession();
        when(sessionMapper.findById(21L)).thenReturn(session);
        InterviewTurnRecord unanswered = turn(InterviewTurnType.MAIN, "问题");
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
        stubModelOutput("好的。");

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
    void streamNextToleratesNullTextGatewayEvents() {
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
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.<InterviewTurnRecord>getArgument(0).setId(140L);
            return null;
        }).when(turnMapper).insert(any(InterviewTurnRecord.class));

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
    }

    private void stubModelOutput(String text) {
        when(modelGateway.stream(any(AiInvocation.class)))
                .thenReturn(Flux.just(new AiStreamEvent(
                        "delta", text, null, null, null, false, null, null, null)));
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
