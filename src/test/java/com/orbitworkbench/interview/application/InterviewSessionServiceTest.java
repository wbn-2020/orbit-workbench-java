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

import com.orbitworkbench.interview.api.InterviewDtos;
import com.orbitworkbench.interview.api.InterviewDtos.AddTurnRequest;
import com.orbitworkbench.interview.api.InterviewDtos.CreateSessionRequest;
import com.orbitworkbench.interview.api.InterviewDtos.SessionResponse;
import com.orbitworkbench.interview.api.InterviewDtos.SubmitAnswerRequest;
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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InterviewSessionServiceTest {

    @Mock
    private InterviewSessionMapper sessionMapper;

    @Mock
    private InterviewTurnMapper turnMapper;

    @Mock
    private InterviewReportMapper reportMapper;

    @Mock
    private com.orbitworkbench.aiconnection.application.AiConnectionService aiConnectionService;

    @Mock
    private com.orbitworkbench.project.infrastructure.mapper.ProjectMapper projectMapper;

    @Mock
    private com.orbitworkbench.knowledge.infrastructure.mapper.ProjectFactMapper projectFactMapper;

    @Mock
    private com.orbitworkbench.interviewer.application.InterviewerService interviewerService;

    private InterviewSessionService service;

    @BeforeEach
    void setUp() {
        service = new InterviewSessionService(sessionMapper, turnMapper, reportMapper,
                aiConnectionService, projectMapper, projectFactMapper, interviewerService,
                new com.fasterxml.jackson.databind.ObjectMapper());
        org.mockito.Mockito.when(aiConnectionService.getRuntimeConfig(5L)).thenReturn(
                new com.orbitworkbench.aiconnection.application.AiConnectionService.AiConnectionRuntimeConfig(
                        5L, 9L, "主账户", "https://gw.example/v1", "/chat/completions",
                        "CHAT_COMPLETIONS", "gpt-4o", "sk-test", 60000));
    }

    @Test
    void createReturnsReadySessionWithConfigSnapshot() {
        doInsertAssignsId();

        SessionResponse response = service.create(7L, request());

        ArgumentCaptor<InterviewSessionRecord> captor =
                ArgumentCaptor.forClass(InterviewSessionRecord.class);
        verify(sessionMapper).insert(captor.capture());
        assertEquals(21L, response.id());
        assertEquals(7L, captor.getValue().getUserId());
        assertEquals(InterviewSessionStatus.READY, captor.getValue().getStatus());
        assertEquals(4, captor.getValue().getQuestionLimit());
        assertEquals(12, captor.getValue().getTurnLimit());
    }

    @Test
    void startTransitionsReadyToRunning() {
        InterviewSessionRecord session = session(InterviewSessionStatus.READY);
        when(sessionMapper.findById(21L)).thenReturn(session);
        when(sessionMapper.updateStatus(eq(21L), eq(InterviewSessionStatus.READY),
                eq(InterviewSessionStatus.RUNNING), any(), isNull(), any())).thenReturn(1);

        service.start(7L, 21L);

        verify(sessionMapper).updateStatus(eq(21L), eq(InterviewSessionStatus.READY),
                eq(InterviewSessionStatus.RUNNING), any(), isNull(), any());
    }

    @Test
    void startRejectsPausedSession() {
        InterviewSessionRecord session = session(InterviewSessionStatus.PAUSED);
        when(sessionMapper.findById(21L)).thenReturn(session);

        assertThrows(ApiException.class, () -> service.start(7L, 21L));
        verify(sessionMapper, never()).updateStatus(anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void addTurnEnforcesTotalTurnLimit() {
        InterviewSessionRecord session = session(InterviewSessionStatus.RUNNING);
        session.setTurnLimit(2);
        when(sessionMapper.findById(21L)).thenReturn(session);
        when(turnMapper.countBySession(21L)).thenReturn(2);

        assertThrows(ApiException.class,
                () -> service.addTurn(7L, 21L, new AddTurnRequest("MAIN", "问题")));
        verify(turnMapper, never()).insert(any(InterviewTurnRecord.class));
    }

    @Test
    void addTurnEnforcesMainQuestionLimit() {
        InterviewSessionRecord session = session(InterviewSessionStatus.RUNNING);
        session.setQuestionLimit(1);
        when(sessionMapper.findById(21L)).thenReturn(session);
        when(turnMapper.countBySession(21L)).thenReturn(1);
        when(turnMapper.countBySessionAndType(21L, InterviewTurnType.MAIN)).thenReturn(1);

        assertThrows(ApiException.class,
                () -> service.addTurn(7L, 21L, new AddTurnRequest("MAIN", "第二问")));
        verify(turnMapper, never()).insert(any(InterviewTurnRecord.class));
    }

    @Test
    void addTurnRejectedWhenPaused() {
        InterviewSessionRecord session = session(InterviewSessionStatus.PAUSED);
        when(sessionMapper.findById(21L)).thenReturn(session);

        assertThrows(ApiException.class,
                () -> service.addTurn(7L, 21L, new AddTurnRequest("MAIN", "问题")));
    }

    @Test
    void addTurnPersistsQuestionWithSequentialNumber() {
        InterviewSessionRecord session = session(InterviewSessionStatus.RUNNING);
        when(sessionMapper.findById(21L)).thenReturn(session);
        when(turnMapper.countBySession(21L)).thenReturn(0);
        doInsertTurnAssignsId();

        var response = service.addTurn(7L, 21L, new AddTurnRequest("MAIN", "先介绍秒杀架构"));

        ArgumentCaptor<InterviewTurnRecord> captor =
                ArgumentCaptor.forClass(InterviewTurnRecord.class);
        verify(turnMapper).insert(captor.capture());
        assertEquals(1, captor.getValue().getTurnNo());
        assertEquals(1L, response.id());
        assertEquals("先介绍秒杀架构", captor.getValue().getQuestion());
    }

    @Test
    void endFromRunningCreatesPendingReportAndCompletingSession() {
        InterviewSessionRecord session = session(InterviewSessionStatus.RUNNING);
        when(sessionMapper.findById(21L)).thenReturn(session);
        when(sessionMapper.updateStatus(eq(21L), eq(InterviewSessionStatus.RUNNING),
                eq(InterviewSessionStatus.COMPLETING), isNull(), any(), any())).thenReturn(1);

        service.end(7L, 21L);

        ArgumentCaptor<InterviewReportRecord> captor =
                ArgumentCaptor.forClass(InterviewReportRecord.class);
        verify(reportMapper).insert(captor.capture());
        assertEquals(ReportStatus.REPORT_PENDING, captor.getValue().getStatus());
        assertEquals(21L, captor.getValue().getSessionId());
    }

    @Test
    void endRejectsReadySession() {
        InterviewSessionRecord session = session(InterviewSessionStatus.READY);
        when(sessionMapper.findById(21L)).thenReturn(session);

        assertThrows(ApiException.class, () -> service.end(7L, 21L));
        verify(reportMapper, never()).insert(any(InterviewReportRecord.class));
    }

    @Test
    void submitAnswerRejectedTwice() {
        InterviewSessionRecord session = session(InterviewSessionStatus.RUNNING);
        InterviewTurnRecord turn = new InterviewTurnRecord();
        turn.setId(9L);
        turn.setSessionId(21L);
        turn.setAnswer("已回答");
        when(sessionMapper.findById(21L)).thenReturn(session);
        when(turnMapper.findById(9L)).thenReturn(turn);

        assertThrows(ApiException.class, () -> service.submitAnswer(7L, 21L, 9L,
                new SubmitAnswerRequest("回答", "INDEPENDENT")));
        verify(turnMapper, never()).submitAnswer(anyLong(), any(), any(), any());
    }

    @Test
    void anotherUsersSessionIsNotFound() {
        InterviewSessionRecord session = session(InterviewSessionStatus.READY);
        session.setUserId(8L);
        when(sessionMapper.findById(21L)).thenReturn(session);

        assertThrows(ApiException.class, () -> service.start(7L, 21L));
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

    private void doInsertAssignsId() {
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.<InterviewSessionRecord>getArgument(0).setId(21L);
            return null;
        }).when(sessionMapper).insert(any(InterviewSessionRecord.class));
    }

    private void doInsertTurnAssignsId() {
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.<InterviewTurnRecord>getArgument(0).setId(1L);
            return null;
        }).when(turnMapper).insert(any(InterviewTurnRecord.class));
    }

    private CreateSessionRequest request() {
        return request(null);
    }

    private CreateSessionRequest request(
            List<InterviewDtos.ProjectBindingRequest> bindings) {
        return request(null, bindings);
    }

    private CreateSessionRequest request(Long interviewerId,
                                         List<InterviewDtos.ProjectBindingRequest> bindings) {
        return new CreateSessionRequest(
                "字节跳动 · 技术面",
                "PROJECT_DEEP_DIVE",
                "TRAINING",
                "FIRST",
                interviewerId,
                "项目深挖面试官",
                "Java 后端工程师",
                "THREE_TO_FIVE_YEARS",
                4,
                3,
                12,
                45,
                null,
                5L,
                "DISABLED",
                bindings);
    }

    @Test
    void createSnapshotsInterviewerProfile() {
        doInsertAssignsId();
        when(interviewerService.requireUsable(7L, 11L)).thenReturn(profile(11L));
        when(interviewerService.parseTags("[\"并发\",\"JVM\"]"))
                .thenReturn(List.of("并发", "JVM"));

        SessionResponse response = service.create(7L, request(11L, null));

        ArgumentCaptor<InterviewSessionRecord> captor =
                ArgumentCaptor.forClass(InterviewSessionRecord.class);
        verify(sessionMapper).insert(captor.capture());
        assertEquals(11L, captor.getValue().getInterviewerId());
        assertEquals("项目深挖面试官", captor.getValue().getInterviewerNameSnapshot());
        String json = captor.getValue().getInterviewerSnapshotJson();
        assertEquals(true, json.contains("\"profileId\":11"));
        assertEquals(true, json.contains("只能依据会话项目快照提问"));
        assertEquals(true, json.contains("\"focusTags\":[\"并发\",\"JVM\"]"));
        assertEquals(11L, response.interviewerId());
    }

    @Test
    void createRejectsUnusableInterviewer() {
        when(interviewerService.requireUsable(7L, 11L)).thenThrow(
                new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,
                        com.orbitworkbench.shared.api.ErrorCode.INVALID_REQUEST, "面试官已归档"));

        assertThrows(ApiException.class, () -> service.create(7L, request(11L, null)));
        verify(sessionMapper, never()).insert(any(InterviewSessionRecord.class));
    }

    private com.orbitworkbench.interviewer.domain.InterviewerProfileRecord profile(Long id) {
        com.orbitworkbench.interviewer.domain.InterviewerProfileRecord record =
                new com.orbitworkbench.interviewer.domain.InterviewerProfileRecord();
        record.setId(id);
        record.setName("项目深挖面试官");
        record.setSystemPrompt("只能依据会话项目快照提问");
        record.setTopicMode("PROJECT_DEEP_DIVE");
        record.setFocusTagsJson("[\"并发\",\"JVM\"]");
        record.setDefaultQuestionLimit(6);
        record.setDefaultFollowUpLimit(5);
        record.setBuiltIn(true);
        record.setVersion(1);
        return record;
    }

    @Test
    void createSnapshotsConfirmedFactsOfBoundVersion() {
        doInsertAssignsId();
        mockBinding(31L, 61L, "秒杀中台", 3);

        SessionResponse response = service.create(7L,
                request(List.of(new InterviewDtos.ProjectBindingRequest(31L, 61L))));

        ArgumentCaptor<InterviewSessionRecord> captor =
                ArgumentCaptor.forClass(InterviewSessionRecord.class);
        verify(sessionMapper).insert(captor.capture());
        String json = captor.getValue().getProjectBindingsJson();
        assertEquals(true, json.contains("\"projectName\":\"秒杀中台\""));
        assertEquals(true, json.contains("\"versionNumber\":3"));
        assertEquals(true, json.contains("已确认的业务背景"));
        assertEquals(false, json.contains("待确认的分析"));
        assertEquals(false, json.contains("已归档的事实"));
        assertEquals(1, response.projectBindings().size());
        assertEquals("秒杀中台", response.projectBindings().get(0).projectName());
        assertEquals(61L, response.projectBindings().get(0).versionId());
        assertEquals(1, response.projectBindings().get(0).factCount());
    }

    @Test
    void createWithoutBindingsKeepsEmptySnapshot() {
        doInsertAssignsId();

        SessionResponse response = service.create(7L, request());

        ArgumentCaptor<InterviewSessionRecord> captor =
                ArgumentCaptor.forClass(InterviewSessionRecord.class);
        verify(sessionMapper).insert(captor.capture());
        assertEquals("[]", captor.getValue().getProjectBindingsJson());
        assertEquals(List.of(), response.projectBindings());
    }

    @Test
    void createRejectsUnknownProject() {
        when(projectMapper.findProjectByIdAndUserId(31L, 7L)).thenReturn(null);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.create(7L,
                        request(List.of(new InterviewDtos.ProjectBindingRequest(31L, 61L)))));
        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, exception.getStatus());
        verify(sessionMapper, never()).insert(any(InterviewSessionRecord.class));
    }

    @Test
    void createRejectsVersionOfAnotherProject() {
        when(projectMapper.findProjectByIdAndUserId(31L, 7L)).thenReturn(project(31L, "秒杀中台"));
        com.orbitworkbench.project.domain.ProjectVersionRecord version =
                new com.orbitworkbench.project.domain.ProjectVersionRecord();
        version.setId(61L);
        version.setProjectId(32L);
        version.setVersionNumber(1);
        when(projectMapper.findVersionById(61L)).thenReturn(version);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.create(7L,
                        request(List.of(new InterviewDtos.ProjectBindingRequest(31L, 61L)))));
        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, exception.getStatus());
        verify(sessionMapper, never()).insert(any(InterviewSessionRecord.class));
    }

    @Test
    void createRejectsDuplicateBinding() {
        ApiException exception = assertThrows(ApiException.class,
                () -> service.create(7L, request(List.of(
                        new InterviewDtos.ProjectBindingRequest(31L, 61L),
                        new InterviewDtos.ProjectBindingRequest(31L, 61L)))));
        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, exception.getStatus());
        verify(sessionMapper, never()).insert(any(InterviewSessionRecord.class));
    }

    private void mockBinding(Long projectId, Long versionId, String projectName, int versionNumber) {
        when(projectMapper.findProjectByIdAndUserId(projectId, 7L))
                .thenReturn(project(projectId, projectName));
        com.orbitworkbench.project.domain.ProjectVersionRecord version =
                new com.orbitworkbench.project.domain.ProjectVersionRecord();
        version.setId(versionId);
        version.setProjectId(projectId);
        version.setVersionNumber(versionNumber);
        when(projectMapper.findVersionById(versionId)).thenReturn(version);
        when(projectFactMapper.listByVersion(7L, versionId)).thenReturn(List.of(
                fact(71L, "已确认的业务背景", com.orbitworkbench.knowledge.domain.FactStatus.CONFIRMED),
                fact(72L, "待确认的分析", com.orbitworkbench.knowledge.domain.FactStatus.ANALYZED),
                fact(73L, "已归档的事实", com.orbitworkbench.knowledge.domain.FactStatus.ARCHIVED)));
    }

    private com.orbitworkbench.project.domain.ProjectRecord project(Long id, String name) {
        com.orbitworkbench.project.domain.ProjectRecord record =
                new com.orbitworkbench.project.domain.ProjectRecord();
        record.setId(id);
        record.setName(name);
        return record;
    }

    private com.orbitworkbench.knowledge.domain.ProjectFactRecord fact(
            Long id, String title, com.orbitworkbench.knowledge.domain.FactStatus status) {
        com.orbitworkbench.knowledge.domain.ProjectFactRecord record =
                new com.orbitworkbench.knowledge.domain.ProjectFactRecord();
        record.setId(id);
        record.setFactType("BUSINESS");
        record.setTitle(title);
        record.setContent(title + "的详细内容");
        record.setConfirmationStatus(status);
        return record;
    }

    @Test
    void pauseTransitionsRunningToPausedWithoutTouchingTimestamps() {
        InterviewSessionRecord running = session(InterviewSessionStatus.RUNNING);
        when(sessionMapper.findById(21L)).thenReturn(running, session(InterviewSessionStatus.PAUSED));
        when(sessionMapper.updateStatus(eq(21L), eq(InterviewSessionStatus.RUNNING),
                eq(InterviewSessionStatus.PAUSED), isNull(), isNull(), any())).thenReturn(1);

        SessionResponse response = service.pause(7L, 21L);

        assertEquals("PAUSED", response.status());
        verify(sessionMapper).updateStatus(eq(21L), eq(InterviewSessionStatus.RUNNING),
                eq(InterviewSessionStatus.PAUSED), isNull(), isNull(), any());
    }

    @Test
    void pauseRejectsReadySession() {
        when(sessionMapper.findById(21L)).thenReturn(session(InterviewSessionStatus.READY));

        ApiException exception = assertThrows(ApiException.class, () -> service.pause(7L, 21L));

        assertEquals("状态不允许该操作：当前 READY，该操作要求 RUNNING，目标 PAUSED",
                exception.getMessage());
        verify(sessionMapper, never()).updateStatus(anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void resumeTransitionsPausedToRunning() {
        when(sessionMapper.findById(21L)).thenReturn(
                session(InterviewSessionStatus.PAUSED), session(InterviewSessionStatus.RUNNING));
        when(sessionMapper.updateStatus(eq(21L), eq(InterviewSessionStatus.PAUSED),
                eq(InterviewSessionStatus.RUNNING), isNull(), isNull(), any())).thenReturn(1);

        SessionResponse response = service.resume(7L, 21L);

        assertEquals("RUNNING", response.status());
    }

    @Test
    void resumeRejectsRunningSession() {
        when(sessionMapper.findById(21L)).thenReturn(session(InterviewSessionStatus.RUNNING));

        assertThrows(ApiException.class, () -> service.resume(7L, 21L));
        verify(sessionMapper, never()).updateStatus(anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void cancelFromPausedMarksSessionCancelledWithEndedAt() {
        when(sessionMapper.findById(21L)).thenReturn(
                session(InterviewSessionStatus.PAUSED), session(InterviewSessionStatus.CANCELLED));
        when(sessionMapper.updateStatus(eq(21L), eq(InterviewSessionStatus.PAUSED),
                eq(InterviewSessionStatus.CANCELLED), isNull(), any(), any())).thenReturn(1);

        SessionResponse response = service.cancel(7L, 21L);

        assertEquals("CANCELLED", response.status());
        verify(sessionMapper).updateStatus(eq(21L), eq(InterviewSessionStatus.PAUSED),
                eq(InterviewSessionStatus.CANCELLED), isNull(), any(), any());
    }

    @Test
    void cancelRejectsRunningSession() {
        when(sessionMapper.findById(21L)).thenReturn(session(InterviewSessionStatus.RUNNING));

        assertThrows(ApiException.class, () -> service.cancel(7L, 21L));
        verify(sessionMapper, never()).updateStatus(anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void endIsAllowedFromPaused() {
        when(sessionMapper.findById(21L)).thenReturn(
                session(InterviewSessionStatus.PAUSED), session(InterviewSessionStatus.COMPLETING));
        when(sessionMapper.updateStatus(eq(21L), eq(InterviewSessionStatus.PAUSED),
                eq(InterviewSessionStatus.COMPLETING), isNull(), any(), any())).thenReturn(1);

        SessionResponse response = service.end(7L, 21L);

        assertEquals("COMPLETING", response.status());
        verify(reportMapper).insert(any(InterviewReportRecord.class));
    }

    @Test
    void transitionRejectsConcurrentStatusChange() {
        when(sessionMapper.findById(21L)).thenReturn(session(InterviewSessionStatus.RUNNING));
        when(sessionMapper.updateStatus(eq(21L), eq(InterviewSessionStatus.RUNNING),
                eq(InterviewSessionStatus.PAUSED), isNull(), isNull(), any())).thenReturn(0);

        ApiException exception = assertThrows(ApiException.class, () -> service.pause(7L, 21L));

        assertEquals("会话状态已变化，请刷新后重试", exception.getMessage());
    }

    @Test
    void endRejectsConcurrentStatusChangeAndCreatesNoReport() {
        when(sessionMapper.findById(21L)).thenReturn(session(InterviewSessionStatus.RUNNING));
        when(sessionMapper.updateStatus(eq(21L), eq(InterviewSessionStatus.RUNNING),
                eq(InterviewSessionStatus.COMPLETING), isNull(), any(), any())).thenReturn(0);

        assertThrows(ApiException.class, () -> service.end(7L, 21L));

        verify(reportMapper, never()).insert(any(InterviewReportRecord.class));
    }

    @Test
    void questionAndAnswerRejectedOnCancelledSession() {
        when(sessionMapper.findById(21L)).thenReturn(session(InterviewSessionStatus.CANCELLED));

        assertThrows(ApiException.class,
                () -> service.addTurn(7L, 21L, new AddTurnRequest("MAIN", "问题")));
        verify(turnMapper, never()).insert(any(InterviewTurnRecord.class));
    }
}
