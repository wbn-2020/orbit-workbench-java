package com.orbitworkbench.agent.application;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orbitworkbench.agent.api.AgentRunDtos.AgentRunResponse;
import com.orbitworkbench.agent.domain.AgentRunRecord;
import com.orbitworkbench.agent.domain.RunEventRecord;
import com.orbitworkbench.agent.infrastructure.mapper.AgentRunMapper;
import com.orbitworkbench.agent.infrastructure.mapper.ModelCallMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.task.application.TaskService;
import com.orbitworkbench.task.domain.TaskRecord;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class AgentRunServiceTest {

    @Mock
    private AgentRunMapper agentRunMapper;
    @Mock
    private ModelCallMapper modelCallMapper;
    @Mock
    private AgentDefinitionService agentDefinitionService;
    @Mock
    private TaskService taskService;
    @Mock
    private RunEventService runEventService;
    @Mock
    private SseHub sseHub;
    @Mock
    private ThreadPoolTaskExecutor agentTaskExecutor;
    @Mock
    private AgentRunWorker agentRunWorker;
    @Mock
    private TransactionTemplate transactionTemplate;

    private AgentRunService service;

    @BeforeEach
    void setUp() {
        service = new AgentRunService(
                agentRunMapper,
                modelCallMapper,
                agentDefinitionService,
                taskService,
                runEventService,
                sseHub,
                agentTaskExecutor,
                agentRunWorker,
                transactionTemplate);
    }

    @ParameterizedTest
    @CsvSource({
            "QUEUED, CANCELLED, run.cancelled, 运行已取消",
            "RUNNING, CANCELLING, run.cancel.requested, 已请求取消运行",
            "WAITING_USER, CANCELLED, run.cancelled, 运行已取消",
            "PAUSED, CANCELLED, run.cancelled, 运行已取消",
            "PAUSING, CANCELLING, run.cancel.requested, 已请求取消运行"
    })
    void cancelMapsAllowedSourceStatusToTargetStatus(String sourceStatus,
                                                     String targetStatus,
                                                     String eventType,
                                                     String eventSummary) {
        AgentRunRecord before = run(sourceStatus);
        AgentRunRecord after = run(targetStatus);
        RunEventRecord event = new RunEventRecord();
        when(agentRunMapper.findByIdForUpdate(11L)).thenReturn(before);
        boolean immediate = "CANCELLED".equals(targetStatus);
        if (immediate) {
            when(agentRunMapper.updateStatus(
                    eq(11L),
                    eq(sourceStatus),
                    eq("CANCELLED"),
                    eq(ErrorCode.CANCELLED.name()),
                    eq("运行已取消"),
                    any(Instant.class)))
                    .thenReturn(1);
        } else {
            when(agentRunMapper.requestCancel(eq(11L), anyList())).thenReturn(1);
        }
        when(runEventService.append(
                eq(11L), isNull(), eq(eventType), eq(eventSummary),
                eq(Map.of("status", targetStatus)))).thenReturn(event);
        when(agentRunMapper.findById(11L)).thenReturn(after);

        AgentRunResponse response = service.cancel(11L);

        assertEquals(targetStatus, response.status());
        verify(sseHub).publishAfterCommit(event);
        if (immediate) {
            verify(taskService).updateRunStatus(22L, 11L, "CANCELLED");
        } else {
            verify(taskService, never()).updateRunStatus(22L, 11L, "CANCELLED");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"FAILED", "SUCCEEDED", "RECOVERY_REQUIRED"})
    void cancelRejectsTerminalStatuses(String status) {
        when(agentRunMapper.findByIdForUpdate(11L)).thenReturn(run(status));

        ApiException exception = assertThrows(ApiException.class, () -> service.cancel(11L));

        assertStateConflict(exception);
        verify(agentRunMapper, never()).requestCancel(eq(11L), anyList());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CANCELLING", "CANCELLED"})
    void repeatedCancelReturnsCurrentSnapshot(String status) {
        when(agentRunMapper.findByIdForUpdate(11L)).thenReturn(run(status));

        AgentRunResponse response = service.cancel(11L);

        assertEquals(status, response.status());
        verify(agentRunMapper, never()).requestCancel(eq(11L), anyList());
        verify(runEventService, never()).append(
                any(), any(), any(), any(), any());
    }

    @Test
    void resumeCreatesSuccessorRunAndKeepsSourcePaused() {
        RunEventRecord startedEvent = new RunEventRecord();
        RunEventRecord resumedEvent = new RunEventRecord();
        TaskRecord task = task("PAUSED");
        AgentRunRecord child = run("QUEUED");
        child.setId(12L);
        child.setRetryOfRunId(11L);
        when(agentRunMapper.findByIdForUpdate(11L)).thenReturn(run("PAUSED"));
        when(agentRunMapper.findLatestRetryByRunId(11L)).thenReturn(null);
        when(taskService.lockForRun(22L)).thenReturn(task);
        when(agentDefinitionService.requireActiveId("TECH_LEARNING")).thenReturn(44L);
        doAnswer(invocation -> {
            AgentRunRecord inserted = invocation.getArgument(0);
            inserted.setId(12L);
            return null;
        }).when(agentRunMapper).insert(any(AgentRunRecord.class));
        when(runEventService.append(
                eq(12L), isNull(), eq("run.started"), eq("运行已排队"),
                eq(Map.of("status", "QUEUED")))).thenReturn(startedEvent);
        when(runEventService.append(
                eq(11L), isNull(), eq("run.resumed"), eq("已创建恢复运行"),
                eq(Map.of("status", "PAUSED", "successorRunId", 12L))))
                .thenReturn(resumedEvent);
        when(agentRunMapper.findById(12L)).thenReturn(child);

        AgentRunResponse response = service.resume(11L);

        assertAll(
                () -> assertEquals(12L, response.id()),
                () -> assertEquals("QUEUED", response.status()),
                () -> assertEquals(11L, response.retryOfRunId()),
                  () -> verify(taskService).validateLockedRunInput(task, 33L),
                () -> verify(taskService).attachRun(task, 12L, java.util.Set.of("PAUSED")),
                () -> verify(agentRunMapper, never()).updateStatus(
                        eq(11L), any(), any(), any(), any(), any()),
                () -> verify(sseHub).publishAfterCommit(startedEvent),
                () -> verify(sseHub).publishAfterCommit(resumedEvent),
                () -> verify(agentTaskExecutor).execute(any(Runnable.class)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"QUEUED", "PAUSING", "FAILED", "RUNNING"})
    void resumeRejectsStatusesOtherThanPaused(String status) {
        when(agentRunMapper.findByIdForUpdate(11L)).thenReturn(run(status));
        when(agentRunMapper.findLatestRetryByRunId(11L)).thenReturn(null);

        ApiException exception = assertThrows(ApiException.class, () -> service.resume(11L));

        assertStateConflict(exception);
        verify(taskService, never()).lockForRun(any());
        verify(agentTaskExecutor, never()).execute(any(Runnable.class));
    }

    @Test
    void repeatedResumeReturnsExistingSuccessor() {
        AgentRunRecord child = run("QUEUED");
        child.setId(12L);
        child.setRetryOfRunId(11L);
        when(agentRunMapper.findByIdForUpdate(11L)).thenReturn(run("PAUSED"));
        when(agentRunMapper.findLatestRetryByRunId(11L)).thenReturn(child);

        AgentRunResponse response = service.resume(11L);

        assertEquals(12L, response.id());
        verify(taskService, never()).lockForRun(any());
        verify(agentRunMapper, never()).insert(any());
    }

    @Test
    void repeatedStartReturnsCurrentActiveRun() {
        TaskRecord task = task("RUNNING");
        task.setCurrentRunId(11L);
        AgentRunRecord active = run("RUNNING");
        when(taskService.lockForRun(22L)).thenReturn(task);
        when(agentRunMapper.findById(11L)).thenReturn(active);

        var response = service.start(22L, null);

        assertAll(
                () -> assertEquals(11L, response.runId()),
                () -> assertEquals("RUNNING", response.status()),
                () -> assertEquals("RUNNING", response.taskStatus()));
        verify(taskService, never()).validateLockedRunInput(any());
        verify(agentRunMapper, never()).insert(any());
    }

    @Test
    void retryRejectsNonTerminalRun() {
        when(agentRunMapper.findByIdForUpdate(11L)).thenReturn(run("RUNNING"));

        ApiException exception = assertThrows(ApiException.class, () -> service.retry(11L));

        assertStateConflict(exception);
        verify(agentRunMapper, never()).findLatestRetryByRunId(11L);
    }

    @Test
    void repeatedRetryReturnsExistingChildRun() {
        AgentRunRecord existingRetry = run("QUEUED");
        existingRetry.setId(12L);
        existingRetry.setRetryOfRunId(11L);
        when(agentRunMapper.findByIdForUpdate(11L)).thenReturn(run("FAILED"));
        when(agentRunMapper.findLatestRetryByRunId(11L)).thenReturn(existingRetry);

        AgentRunResponse response = service.retry(11L);

        assertEquals(12L, response.id());
        assertEquals(11L, response.retryOfRunId());
        verify(agentRunMapper, never()).insert(any());
    }

    @Test
    void queueRejectionFinalizesConcurrentPause() {
        TaskRecord task = task("READY");
        RunEventRecord startedEvent = new RunEventRecord();
        RunEventRecord pausedEvent = new RunEventRecord();
        when(taskService.lockForRun(22L)).thenReturn(task);
        when(agentDefinitionService.requireActiveId("TECH_LEARNING")).thenReturn(44L);
        doAnswer(invocation -> {
            AgentRunRecord inserted = invocation.getArgument(0);
            inserted.setId(11L);
            return null;
        }).when(agentRunMapper).insert(any(AgentRunRecord.class));
        when(runEventService.append(
                11L, null, "run.started", "运行已排队", Map.of("status", "QUEUED")))
                .thenReturn(startedEvent);
        doThrow(new RejectedExecutionException("queue full"))
                .when(agentTaskExecutor)
                .execute(any(Runnable.class));
        executeTransactionsImmediately();
        when(agentRunMapper.findByIdForUpdate(11L)).thenReturn(run("PAUSING"));
        when(agentRunMapper.updateStatus(
                11L, "PAUSING", "PAUSED", null, null, null)).thenReturn(1);
        when(runEventService.append(
                11L,
                null,
                "run.paused",
                "运行在进入执行队列前已暂停",
                Map.of("status", "PAUSED")))
                .thenReturn(pausedEvent);

        service.start(22L, null);

        verify(taskService).updateRunStatus(22L, 11L, "PAUSED");
        verify(sseHub).publishAfterCommit(startedEvent);
        verify(sseHub).publish(pausedEvent);
    }

    @Test
    void recoveryFinishesRunningModelCalls() {
        AgentRunRecord interrupted = run("RECOVERY_REQUIRED");
        RunEventRecord failedEvent = new RunEventRecord();
        when(agentRunMapper.findInterruptedIds()).thenReturn(List.of(11L));
        when(agentRunMapper.markRecoveryRequired(11L)).thenReturn(1);
        when(agentRunMapper.findById(11L)).thenReturn(interrupted);
        when(runEventService.append(
                11L,
                null,
                "run.failed",
                "服务重启后运行需要恢复",
                Map.of("status", "RECOVERY_REQUIRED")))
                .thenReturn(failedEvent);

        service.recoverInterruptedRuns();

        verify(modelCallMapper).finishRunningByRunId(
                eq(11L),
                eq("FAILED"),
                any(Instant.class),
                eq(ErrorCode.STREAM_INTERRUPTED.name()),
                eq("服务重启导致模型调用中断"));
        verify(taskService).updateRunStatus(22L, 11L, "FAILED");
        verify(sseHub).publishAfterCommit(failedEvent);
    }

    private void executeTransactionsImmediately() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(
                    org.mockito.Mockito.mock(TransactionStatus.class));
        });
    }

    private AgentRunRecord run(String status) {
        AgentRunRecord run = new AgentRunRecord();
        run.setId(11L);
        run.setTaskId(22L);
        run.setConnectionId(33L);
        run.setStatus(status);
        return run;
    }

    private TaskRecord task(String status) {
        TaskRecord task = new TaskRecord();
        task.setId(22L);
        task.setModuleType("TECH_LEARNING");
        task.setConnectionId(33L);
        task.setStatus(status);
        return task;
    }

    private void assertStateConflict(ApiException exception) {
        assertAll(
                () -> assertEquals(HttpStatus.CONFLICT, exception.getStatus()),
                () -> assertEquals(ErrorCode.STATE_CONFLICT, exception.getErrorCode()));
    }
}
