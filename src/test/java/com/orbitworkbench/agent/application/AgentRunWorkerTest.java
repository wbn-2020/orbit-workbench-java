package com.orbitworkbench.agent.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orbitworkbench.agent.domain.AgentRunRecord;
import com.orbitworkbench.agent.domain.ModelCallRecord;
import com.orbitworkbench.agent.domain.RunEventRecord;
import com.orbitworkbench.agent.infrastructure.mapper.AgentRunMapper;
import com.orbitworkbench.agent.infrastructure.mapper.ModelCallMapper;
import com.orbitworkbench.ai.application.AiInvocation;
import com.orbitworkbench.ai.application.AiStreamEvent;
import com.orbitworkbench.ai.application.ModelGateway;
import com.orbitworkbench.aiconnection.application.AiConnectionService;
import com.orbitworkbench.aiconnection.application.AiConnectionService.AiConnectionRuntimeConfig;
import com.orbitworkbench.artifact.application.ArtifactService;
import com.orbitworkbench.artifact.application.CreateInitialArtifactCommand;
import com.orbitworkbench.document.application.DocumentService;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.shared.config.AgentRuntimeProperties;
import com.orbitworkbench.task.application.TaskService;
import com.orbitworkbench.task.domain.TaskRecord;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class AgentRunWorkerTest {

    @Mock
    private AgentRunMapper agentRunMapper;
    @Mock
    private ModelCallMapper modelCallMapper;
    @Mock
    private AgentDefinitionService agentDefinitionService;
    @Mock
    private TaskService taskService;
    @Mock
    private AiConnectionService connectionService;
    @Mock
    private DocumentService documentService;
    @Mock
    private ArtifactService artifactService;
    @Mock
    private ModelGateway modelGateway;
    @Mock
    private RunEventService runEventService;
    @Mock
    private SseHub sseHub;
    @Mock
    private TransactionTemplate transactionTemplate;

    private AgentRunWorker worker;
    private AgentRuntimeProperties runtimeProperties;

    @BeforeEach
    void setUp() {
        runtimeProperties = new AgentRuntimeProperties();
        worker = new AgentRunWorker(
                agentRunMapper,
                modelCallMapper,
                agentDefinitionService,
                taskService,
                connectionService,
                documentService,
                artifactService,
                modelGateway,
                runEventService,
                sseHub,
                transactionTemplate,
                runtimeProperties);
    }

    @Test
    void queuedRunContinuesAfterSuccessfulRunningTransition() {
        AgentRunRecord queued = run("QUEUED");
        AgentRunRecord running = run("RUNNING");
        when(agentRunMapper.findById(7L)).thenReturn(queued, running);
        when(agentRunMapper.markRunning(any(Long.class), any(Instant.class))).thenReturn(1);
        when(taskService.requireTask(9L)).thenThrow(new ApiException(
                HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "任务不存在"));

        worker.execute(7L);

        verify(agentRunMapper).markRunning(any(Long.class), any(Instant.class));
        verify(taskService).requireTask(9L);
    }

    @Test
    void formalRunUses4096MaxOutputTokens() {
        prepareRunningExecution(Flux.error(new RuntimeException("stop after invocation")));

        worker.execute(7L);

        ArgumentCaptor<AiInvocation> invocationCaptor =
                ArgumentCaptor.forClass(AiInvocation.class);
        verify(modelGateway).stream(invocationCaptor.capture());
        assertEquals(4096, invocationCaptor.getValue().maxOutputTokens());
    }

    @Test
    void completedRunWithoutTextFailsBeforeCreatingArtifact() {
        prepareRunningExecution(Flux.just(
                new AiStreamEvent("run.completed", null, null, "request-1", true)));
        executeTransactionsImmediately();
        when(agentRunMapper.findByIdForUpdate(7L)).thenReturn(run("RUNNING"));
        when(agentRunMapper.updateStatus(
                eq(7L),
                eq("RUNNING"),
                eq("FAILED"),
                eq(ErrorCode.INVALID_STRUCTURED_OUTPUT.name()),
                eq("模型未返回非空文本内容"),
                any(Instant.class)))
                .thenReturn(1);
        RunEventRecord failedEvent = new RunEventRecord();
        when(runEventService.append(
                eq(7L),
                eq(13L),
                eq("run.failed"),
                eq("模型未返回非空文本内容"),
                eq(Map.of(
                        "status", "FAILED",
                        "errorCode", ErrorCode.INVALID_STRUCTURED_OUTPUT.name()))))
                .thenReturn(failedEvent);

        worker.execute(7L);

        verify(agentRunMapper).updateStatus(
                eq(7L),
                eq("RUNNING"),
                eq("FAILED"),
                eq(ErrorCode.INVALID_STRUCTURED_OUTPUT.name()),
                eq("模型未返回非空文本内容"),
                any(Instant.class));
        verify(taskService).updateRunStatus(9L, 7L, "FAILED");
        verify(sseHub).publish(failedEvent);
        verify(artifactService, never()).createInitialArtifact(any(CreateInitialArtifactCommand.class));
    }

    @Test
    void pauseFlushesBufferedTextBeforeFinalizing() {
        prepareRunningExecution(Flux.just(
                new AiStreamEvent(
                        "output.text.delta",
                        "partial",
                        null,
                        "request-1",
                        false)));
        AgentRunRecord running = run("RUNNING");
        running.setAgentDefinitionId(12L);
        AgentRunRecord pausing = run("PAUSING");
        when(agentRunMapper.findById(7L)).thenReturn(running, pausing);
        when(agentRunMapper.findByIdForUpdate(7L)).thenReturn(pausing);
        when(agentRunMapper.updateStatus(
                7L, "PAUSING", "PAUSED", null, null, null)).thenReturn(1);
        executeTransactionsImmediately();
        RunEventRecord deltaEvent = new RunEventRecord();
        RunEventRecord pausedEvent = new RunEventRecord();
        when(runEventService.append(
                eq(7L),
                eq(13L),
                eq("output.text.delta"),
                isNull(),
                any(Map.class)))
                .thenReturn(deltaEvent);
        when(runEventService.append(
                7L, 13L, "run.paused", "运行已暂停", Map.of("status", "PAUSED")))
                .thenReturn(pausedEvent);

        worker.execute(7L);

        verify(sseHub).publish(deltaEvent);
        verify(taskService).updateRunStatus(9L, 7L, "PAUSED");
        verify(sseHub).publish(pausedEvent);
    }

    @Test
    void pauseFinalizationHonorsConcurrentCancel() {
        AgentRunRecord pausing = run("PAUSING");
        AgentRunRecord cancelling = run("CANCELLING");
        ModelCallRecord call = new ModelCallRecord();
        call.setId(13L);
        when(agentRunMapper.findByIdForUpdate(7L)).thenReturn(cancelling);
        when(agentRunMapper.updateStatus(
                eq(7L),
                eq("CANCELLING"),
                eq("CANCELLED"),
                eq(ErrorCode.CANCELLED.name()),
                eq("运行已取消"),
                any(Instant.class)))
                .thenReturn(1);
        executeTransactionsImmediately();
        RunEventRecord cancelledEvent = new RunEventRecord();
        when(runEventService.append(
                7L, 13L, "run.cancelled", "运行已取消",
                Map.of("status", "CANCELLED")))
                .thenReturn(cancelledEvent);

        worker.finishPaused(pausing, call);

        verify(modelCallMapper).updateFinished(
                eq(13L),
                eq("CANCELLED"),
                any(Instant.class),
                isNull(),
                isNull(),
                isNull(),
                eq(ErrorCode.CANCELLED.name()),
                eq("运行已取消"));
        verify(taskService).updateRunStatus(9L, 7L, "CANCELLED");
        verify(sseHub).publish(cancelledEvent);
    }

    @Test
    void outputLimitFailsRunWithoutCreatingArtifact() {
        runtimeProperties.setMaxOutputCharacters(4);
        prepareRunningExecution(Flux.just(
                new AiStreamEvent(
                        "output.text.delta",
                        "12345",
                        null,
                        "request-1",
                        false)));
        executeTransactionsImmediately();
        when(agentRunMapper.findByIdForUpdate(7L)).thenReturn(run("RUNNING"));
        when(agentRunMapper.updateStatus(
                eq(7L),
                eq("RUNNING"),
                eq("FAILED"),
                eq(ErrorCode.OUTPUT_LIMIT_EXCEEDED.name()),
                eq("模型输出内容超过运行上限"),
                any(Instant.class)))
                .thenReturn(1);
        RunEventRecord failedEvent = new RunEventRecord();
        when(runEventService.append(
                7L,
                13L,
                "run.failed",
                "模型输出内容超过运行上限",
                Map.of(
                        "status", "FAILED",
                        "errorCode", ErrorCode.OUTPUT_LIMIT_EXCEEDED.name())))
                .thenReturn(failedEvent);

        worker.execute(7L);

        verify(taskService).updateRunStatus(9L, 7L, "FAILED");
        verify(sseHub).publish(failedEvent);
        verify(artifactService, never())
                .createInitialArtifact(any(CreateInitialArtifactCommand.class));
    }

    @Test
    void modelFailureHonorsConcurrentCancelUnderRunLock() {
        prepareRunningExecution(Flux.error(new RuntimeException("provider failed")));
        executeTransactionsImmediately();
        when(agentRunMapper.findByIdForUpdate(7L)).thenReturn(run("CANCELLING"));
        when(agentRunMapper.updateStatus(
                eq(7L),
                eq("CANCELLING"),
                eq("CANCELLED"),
                eq(ErrorCode.CANCELLED.name()),
                eq("运行已取消"),
                any(Instant.class)))
                .thenReturn(1);
        RunEventRecord cancelledEvent = new RunEventRecord();
        when(runEventService.append(
                7L,
                13L,
                "run.cancelled",
                "运行已取消",
                Map.of("status", "CANCELLED")))
                .thenReturn(cancelledEvent);

        worker.execute(7L);

        verify(taskService).updateRunStatus(9L, 7L, "CANCELLED");
        verify(sseHub).publish(cancelledEvent);
        verify(agentRunMapper, never()).updateStatus(
                eq(7L),
                eq("RUNNING"),
                eq("FAILED"),
                any(),
                any(),
                any());
    }

    @Test
    void modelFailureHonorsConcurrentPauseUnderRunLock() {
        prepareRunningExecution(Flux.error(new RuntimeException("provider failed")));
        executeTransactionsImmediately();
        when(agentRunMapper.findByIdForUpdate(7L)).thenReturn(run("PAUSING"));
        when(agentRunMapper.updateStatus(
                7L, "PAUSING", "PAUSED", null, null, null)).thenReturn(1);
        RunEventRecord pausedEvent = new RunEventRecord();
        when(runEventService.append(
                7L,
                13L,
                "run.paused",
                "运行已暂停",
                Map.of("status", "PAUSED")))
                .thenReturn(pausedEvent);

        worker.execute(7L);

        verify(taskService).updateRunStatus(9L, 7L, "PAUSED");
        verify(sseHub).publish(pausedEvent);
    }

    private void prepareRunningExecution(Flux<AiStreamEvent> events) {
        AgentRunRecord running = run("RUNNING");
        running.setAgentDefinitionId(12L);
        when(agentRunMapper.findById(7L)).thenReturn(running, running);

        TaskRecord task = new TaskRecord();
        task.setId(9L);
        task.setWorkspaceId(1L);
        task.setConnectionId(11L);
        task.setModuleType("TECH_LEARNING");
        task.setTitle("学习任务");
        task.setExpectedArtifactType("LEARNING_NOTE");
        when(taskService.requireTask(9L)).thenReturn(task);
        when(taskService.findDocumentIds(9L)).thenReturn(List.of());
        when(documentService.readTextsByIds(List.of())).thenReturn(List.of());
        when(agentDefinitionService.requirePrompt(12L)).thenReturn("system prompt");

        AiConnectionRuntimeConfig connection = new AiConnectionRuntimeConfig(
                11L,
                21L,
                "test connection",
                "https://example.invalid",
                "/v1/chat/completions",
                "CHAT_COMPLETIONS",
                "test-model",
                "test-secret",
                30000);
        when(connectionService.getRuntimeConfig(11L)).thenReturn(connection);
        doAnswer(invocation -> {
            ModelCallRecord call = invocation.getArgument(0);
            call.setId(13L);
            return null;
        }).when(modelCallMapper).insert(any(ModelCallRecord.class));
        when(modelGateway.stream(any(AiInvocation.class))).thenReturn(events);
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
        run.setId(7L);
        run.setTaskId(9L);
        run.setConnectionId(11L);
        run.setStatus(status);
        return run;
    }
}
