package com.orbitworkbench.agent.application;

import com.orbitworkbench.ai.application.AiInvocation;
import com.orbitworkbench.ai.application.AiProviderException;
import com.orbitworkbench.ai.application.AiStreamEvent;
import com.orbitworkbench.ai.application.AiUsage;
import com.orbitworkbench.ai.application.ModelGateway;
import com.orbitworkbench.agent.domain.AgentRunRecord;
import com.orbitworkbench.agent.domain.ModelCallRecord;
import com.orbitworkbench.agent.domain.RunEventRecord;
import com.orbitworkbench.agent.infrastructure.mapper.AgentRunMapper;
import com.orbitworkbench.agent.infrastructure.mapper.ModelCallMapper;
import com.orbitworkbench.aiconnection.application.AiConnectionService;
import com.orbitworkbench.aiconnection.application.AiConnectionService.AiConnectionRuntimeConfig;
import com.orbitworkbench.artifact.application.ArtifactService;
import com.orbitworkbench.artifact.application.CreateInitialArtifactCommand;
import com.orbitworkbench.document.api.DocumentDtos.DocumentText;
import com.orbitworkbench.document.application.DocumentService;
import com.orbitworkbench.memory.application.MemoryService;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.shared.config.AgentRuntimeProperties;
import com.orbitworkbench.task.application.TaskService;
import com.orbitworkbench.task.domain.TaskRecord;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@Component
public class AgentRunWorker {

    private static final int AGENT_MAX_OUTPUT_TOKENS = 4096;
    private static final Logger LOGGER = LoggerFactory.getLogger(AgentRunWorker.class);

    private final AgentRunMapper agentRunMapper;
    private final ModelCallMapper modelCallMapper;
    private final AgentDefinitionService agentDefinitionService;
    private final TaskService taskService;
    private final AiConnectionService connectionService;
    private final DocumentService documentService;
    private final ArtifactService artifactService;
    private final ModelGateway modelGateway;
    private final RunEventService runEventService;
    private final SseHub sseHub;
    private final TransactionTemplate transactionTemplate;
    private final AgentRuntimeProperties runtimeProperties;
    private final DataAnalysisAgentRunner dataAnalysisAgentRunner;
    private final MemoryService memoryService;

    @Autowired
    public AgentRunWorker(AgentRunMapper agentRunMapper,
                          ModelCallMapper modelCallMapper,
                          AgentDefinitionService agentDefinitionService,
                          TaskService taskService,
                          AiConnectionService connectionService,
                          DocumentService documentService,
                          ArtifactService artifactService,
                          ModelGateway modelGateway,
                          RunEventService runEventService,
                          SseHub sseHub,
                          TransactionTemplate transactionTemplate,
                          AgentRuntimeProperties runtimeProperties,
                          DataAnalysisAgentRunner dataAnalysisAgentRunner,
                          MemoryService memoryService) {
        this.agentRunMapper = agentRunMapper;
        this.modelCallMapper = modelCallMapper;
        this.agentDefinitionService = agentDefinitionService;
        this.taskService = taskService;
        this.connectionService = connectionService;
        this.documentService = documentService;
        this.artifactService = artifactService;
        this.modelGateway = modelGateway;
        this.runEventService = runEventService;
        this.sseHub = sseHub;
        this.transactionTemplate = transactionTemplate;
        this.runtimeProperties = runtimeProperties;
        this.dataAnalysisAgentRunner = dataAnalysisAgentRunner;
        this.memoryService = memoryService;
    }

    public AgentRunWorker(AgentRunMapper agentRunMapper,
                          ModelCallMapper modelCallMapper,
                          AgentDefinitionService agentDefinitionService,
                          TaskService taskService,
                          AiConnectionService connectionService,
                          DocumentService documentService,
                          ArtifactService artifactService,
                          ModelGateway modelGateway,
                          RunEventService runEventService,
                          SseHub sseHub,
                          TransactionTemplate transactionTemplate,
                          AgentRuntimeProperties runtimeProperties) {
        this(
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
                runtimeProperties,
                null,
                null);
    }

    public void execute(Long runId) {
        AgentRunRecord run = agentRunMapper.findById(runId);
        if (run == null || "CANCELLED".equals(run.getStatus())) {
            return;
        }
        if ("QUEUED".equals(run.getStatus())) {
            if (agentRunMapper.markRunning(runId, Instant.now()) == 1) {
                run.setStatus("RUNNING");
            } else {
                run = agentRunMapper.findById(runId);
            }
        }
        if (run == null) {
            return;
        }
        if ("PAUSING".equals(run.getStatus())) {
            finishPaused(run, null);
            return;
        }
        if (!"RUNNING".equals(run.getStatus())) {
            return;
        }

        TaskRecord task = null;
        AiConnectionRuntimeConfig connection = null;
        ModelCallRecord modelCall = null;
        StringBuilder output = new StringBuilder();
        AtomicBoolean upstreamCompleted = new AtomicBoolean();
        AtomicReference<String> providerRequestId = new AtomicReference<>();
        AtomicReference<AiUsage> usage = new AtomicReference<>();
        AtomicReference<ModelCallRecord> activeDataCall = new AtomicReference<>();
        DataAnalysisRunResult dataAnalysisResult = null;
        TextDeltaBuffer deltaBuffer = TextDeltaBuffer.createDefault();
        StreamBudget streamBudget = new StreamBudget(runtimeProperties);
        Disposable heartbeat = null;
        try {
            agentRunMapper.touchHeartbeat(runId, Instant.now());
            heartbeat = startHeartbeat(runId);
            task = taskService.requireTask(run.getTaskId());
            String systemPrompt = requireRunPrompt(run);
            if (memoryService != null) {
                systemPrompt = memoryService.appendConfirmedInjection(
                        systemPrompt, task.getWorkspaceId());
            }
            Long connectionId = run.getConnectionId() == null
                    ? task.getConnectionId() : run.getConnectionId();
            connection = connectionService.getRuntimeConfig(connectionId);
            if ("DATA_ANALYSIS".equals(task.getModuleType())) {
                if (dataAnalysisAgentRunner == null) {
                    throw new RunExecutionException(
                            ErrorCode.UNSUPPORTED_CAPABILITY,
                            "数据分析 AgentRunner 未配置");
                }
                dataAnalysisResult = dataAnalysisAgentRunner.execute(
                        run,
                        task,
                        connection,
                        systemPrompt,
                        () -> checkControlState(runId),
                        activeDataCall::set);
                modelCall = dataAnalysisResult.finalModelCall();
                completeDataAnalysisSuccess(run, task, dataAnalysisResult);
                return;
            }
            modelCall = createModelCall(run, connection);
            String prompt = buildPrompt(task);
            ModelCallRecord activeModelCall = modelCall;
            modelGateway.stream(new AiInvocation(connection,
                            systemPrompt,
                            prompt, runId, modelCall.getId(), true,
                            AGENT_MAX_OUTPUT_TOKENS))
                    .doOnNext(event -> {
                        streamBudget.accept(event);
                        if (event.providerRequestId() != null) {
                            providerRequestId.set(event.providerRequestId());
                        }
                        if (event.usage() != null) {
                            usage.set(event.usage());
                        }
                        if ("run.completed".equals(event.type()) && event.done()) {
                            flushTextDelta(runId, activeModelCall.getId(),
                                    deltaBuffer, providerRequestId.get());
                            upstreamCompleted.set(true);
                            return;
                        }
                        if ("output.text.delta".equals(event.type())) {
                            if (event.text() != null) {
                                output.append(event.text());
                            }
                            if (deltaBuffer.append(event.text())) {
                                flushTextDelta(runId, activeModelCall.getId(),
                                        deltaBuffer, providerRequestId.get());
                            } else {
                                checkControlState(runId);
                            }
                            return;
                        }
                        flushTextDelta(runId, activeModelCall.getId(),
                                deltaBuffer, providerRequestId.get());
                        if (!"run.started".equals(event.type())) {
                            persistEvent(runId, activeModelCall.getId(), event);
                        } else {
                            checkControlState(runId);
                        }
                    })
                    .blockLast(maxRunDuration());
            flushTextDelta(runId, modelCall.getId(),
                    deltaBuffer, providerRequestId.get());

            AgentRunRecord latest = agentRunMapper.findById(runId);
            if (latest == null || "CANCELLING".equals(latest.getStatus())) {
                finishCancelled(run, modelCall);
                return;
            }
            if ("PAUSING".equals(latest.getStatus())) {
                finishPaused(run, modelCall);
                return;
            }
            if (!upstreamCompleted.get()) {
                throw new RunExecutionException(ErrorCode.STREAM_INTERRUPTED, "模型流未正常完成");
            }
            if (output.toString().isBlank()) {
                throw new RunExecutionException(
                        ErrorCode.INVALID_STRUCTURED_OUTPUT,
                        "模型未返回非空文本内容");
            }
            completeSuccess(run, task, modelCall, output.toString(),
                    providerRequestId.get(), usage.get());
        } catch (AgentRunControlException exception) {
            if (modelCall == null) {
                modelCall = activeDataCall.get();
            }
            flushBufferedTextWithoutControlCheck(
                    run, modelCall, deltaBuffer, providerRequestId.get());
            if (exception.type()
                    == AgentRunControlException.Type.CANCELLED) {
                finishCancelled(run, modelCall);
            } else {
                finishPaused(run, modelCall);
            }
        } catch (Throwable exception) {
            if (modelCall == null) {
                modelCall = activeDataCall.get();
            }
            if (dataAnalysisResult != null && dataAnalysisAgentRunner != null) {
                dataAnalysisAgentRunner.failArtifactStep(
                        dataAnalysisResult,
                        ErrorCode.TOOL_EXECUTION_FAILED,
                        sanitize(exception.getMessage()));
            }
            fail(run, modelCall, normalizeExecutionFailure(exception));
        } finally {
            if (heartbeat != null) {
                heartbeat.dispose();
            }
            if (connection != null) {
                clearSecret(connection);
            }
        }
    }

    private ModelCallRecord createModelCall(AgentRunRecord run, AiConnectionRuntimeConfig connection) {
        ModelCallRecord call = new ModelCallRecord();
        call.setAgentRunId(run.getId());
        call.setRequestId("call_" + UUID.randomUUID().toString().replace("-", ""));
        call.setConnectionId(connection.connectionId());
        call.setModelProfileId(connection.modelProfileId());
        call.setConnectionNameSnapshot(connection.connectionName());
        call.setModelNameSnapshot(connection.modelName());
        call.setProtocol(connection.protocol());
        call.setStreaming(true);
        call.setStatus("RUNNING");
        call.setStartedAt(Instant.now());
        call.setRetryCount(0);
        call.setCreatedAt(Instant.now());
        modelCallMapper.insert(call);
        return call;
    }

    private String buildPrompt(TaskRecord task) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("任务标题：").append(task.getTitle()).append('\n');
        if (task.getDescription() != null) {
            prompt.append("任务说明：").append(task.getDescription()).append('\n');
        }
        List<DocumentText> documents = documentService.readTextsByIds(taskService.findDocumentIds(task.getId()));
        if (!documents.isEmpty()) {
            prompt.append("\n参考资料：\n");
            for (DocumentText document : documents) {
                prompt.append("\n--- ").append(document.name()).append(" ---\n")
                        .append(document.content()).append('\n');
            }
        }
        prompt.append("\n请直接输出 Markdown 学习笔记和练习题，不要输出调用过程。");
        return prompt.toString();
    }

    private void persistEvent(Long runId, Long modelCallId, AiStreamEvent event) {
        persistEvent(runId, modelCallId, event, true);
    }

    private void persistEvent(Long runId,
                              Long modelCallId,
                              AiStreamEvent event,
                              boolean checkControl) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("text", event.text());
        data.put("done", event.done());
        data.put("providerRequestId", event.providerRequestId());
        if (event.usage() != null) {
            data.put("inputTokens", event.usage().inputTokens());
            data.put("outputTokens", event.usage().outputTokens());
        }
        RunEventRecord persisted = runEventService.append(runId, modelCallId, event.type(),
                event.errorSummary(), data);
        sseHub.publish(persisted);
        if (checkControl) {
            checkControlState(runId);
        }
    }

    private void flushTextDelta(Long runId,
                                Long modelCallId,
                                TextDeltaBuffer deltaBuffer,
                                String providerRequestId) {
        if (!deltaBuffer.hasContent()) {
            return;
        }
        persistEvent(runId, modelCallId,
                new AiStreamEvent(
                        "output.text.delta",
                        deltaBuffer.drain(),
                        null,
                        providerRequestId,
                        false));
    }

    private void flushBufferedTextWithoutControlCheck(AgentRunRecord run,
                                                      ModelCallRecord modelCall,
                                                      TextDeltaBuffer deltaBuffer,
                                                      String providerRequestId) {
        if (run == null || modelCall == null || !deltaBuffer.hasContent()) {
            return;
        }
        persistEvent(
                run.getId(),
                modelCall.getId(),
                new AiStreamEvent(
                        "output.text.delta",
                        deltaBuffer.drain(),
                        null,
                        providerRequestId,
                        false),
                false);
    }

    private void checkControlState(Long runId) {
        AgentRunRecord latest = agentRunMapper.findById(runId);
        if (latest == null) {
            throw new RunExecutionException(ErrorCode.RESOURCE_NOT_FOUND, "运行不存在");
        }
        if ("CANCELLING".equals(latest.getStatus())) {
            throw new AgentRunControlException(
                    AgentRunControlException.Type.CANCELLED);
        }
        if ("PAUSING".equals(latest.getStatus())) {
            throw new AgentRunControlException(
                    AgentRunControlException.Type.PAUSED);
        }
    }

    private void completeSuccess(AgentRunRecord run,
                                 TaskRecord task,
                                 ModelCallRecord call,
                                 String output,
                                 String providerRequestId,
                                 AiUsage usage) {
        RunEventRecord event = transactionTemplate.execute(status -> {
            modelCallMapper.updateFinished(call.getId(), "SUCCEEDED", Instant.now(),
                    providerRequestId,
                    usage == null ? null : usage.inputTokens(),
                    usage == null ? null : usage.outputTokens(),
                    null, null);
            if (agentRunMapper.updateStatus(run.getId(), "RUNNING", "SUCCEEDED",
                    null, null, Instant.now()) != 1) {
                throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                        "运行状态已变化，不能完成当前运行");
            }
            taskService.updateRunStatus(task.getId(), run.getId(), "SUCCEEDED");
            artifactService.createInitialArtifact(new CreateInitialArtifactCommand(
                    task.getWorkspaceId(), task.getId(), run.getId(),
                    task.getExpectedArtifactType(), task.getTitle(), output,
                    "MARKDOWN", "技术学习 Agent 初始生成"));
            return runEventService.append(run.getId(), call.getId(), "run.completed",
                    "运行已完成",
                    Map.of("status", "SUCCEEDED", "outputLength", output.length()));
        });
        if (event != null) {
            sseHub.publish(event);
        }
    }

    private void completeDataAnalysisSuccess(
            AgentRunRecord run,
            TaskRecord task,
            DataAnalysisRunResult result) {
        transactionTemplate.executeWithoutResult(status -> {
            ModelCallRecord call = result.finalModelCall();
            modelCallMapper.updateFinished(
                    call.getId(),
                    "SUCCEEDED",
                    Instant.now(),
                    result.providerRequestId(),
                    result.usage() == null
                            ? null : result.usage().inputTokens(),
                    result.usage() == null
                            ? null : result.usage().outputTokens(),
                    null,
                    null);
            if (agentRunMapper.updateStatus(
                    run.getId(),
                    "RUNNING",
                    "SUCCEEDED",
                    null,
                    null,
                    Instant.now()) != 1) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        ErrorCode.STATE_CONFLICT,
                        "运行状态已变化，不能完成当前运行");
            }
            taskService.updateRunStatus(
                    task.getId(), run.getId(), "SUCCEEDED");
            artifactService.createInitialArtifact(
                    new CreateInitialArtifactCommand(
                            task.getWorkspaceId(),
                            task.getId(),
                            run.getId(),
                            "ANALYSIS_REPORT",
                            task.getTitle() + " - 分析报告",
                            result.report(),
                            "MARKDOWN",
                            "数据分析 Agent 初始生成"));
            int index = 1;
            for (String chartSpec : result.chartSpecs()) {
                artifactService.createInitialArtifact(
                        new CreateInitialArtifactCommand(
                                task.getWorkspaceId(),
                                task.getId(),
                                run.getId(),
                                "CHART_SPEC",
                                task.getTitle() + " - 图表 " + index++,
                                chartSpec,
                                "JSON",
                                "数据分析 Agent 图表规格"));
            }
        });
        dataAnalysisAgentRunner.completeArtifactStep(result);
        RunEventRecord event = runEventService.append(
                run.getId(),
                result.finalModelCall().getId(),
                "run.completed",
                "数据分析运行已完成",
                Map.of(
                        "status", "SUCCEEDED",
                        "outputLength", result.report().length(),
                        "chartCount", result.chartSpecs().size()));
        sseHub.publish(event);
    }

    private void finishCancelled(AgentRunRecord run, ModelCallRecord call) {
        RunEventRecord event = transactionTemplate.execute(status -> {
            AgentRunRecord latest = requireRunForFinalization(run.getId());
            if (isTerminal(latest.getStatus())) {
                return null;
            }
            return finalizeCancelledLocked(run, call, latest.getStatus());
        });
        if (event != null) {
            sseHub.publish(event);
        }
    }

    void finishPaused(AgentRunRecord run, ModelCallRecord call) {
        RunEventRecord event = transactionTemplate.execute(status -> {
            AgentRunRecord latest = requireRunForFinalization(run.getId());
            if (isTerminal(latest.getStatus())) {
                return null;
            }
            if ("CANCELLING".equals(latest.getStatus())) {
                return finalizeCancelledLocked(run, call, latest.getStatus());
            }
            if (!List.of("QUEUED", "RUNNING", "WAITING_USER", "PAUSING")
                    .contains(latest.getStatus())) {
                throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                        "运行状态已变化，不能完成暂停");
            }
            return finalizePausedLocked(run, call, latest.getStatus());
        });
        if (event != null) {
            sseHub.publish(event);
        }
    }

    private void fail(AgentRunRecord run, ModelCallRecord call, Throwable exception) {
        ErrorCode errorCode = ErrorCode.UNKNOWN_PROVIDER_ERROR;
        String summary = "运行失败";
        if (exception instanceof AiProviderException providerException) {
            errorCode = providerException.getErrorCode();
            summary = providerException.getMessage();
        } else if (exception instanceof ApiException apiException) {
            errorCode = apiException.getErrorCode();
            summary = apiException.getMessage();
        } else if (exception instanceof TimeoutException) {
            errorCode = ErrorCode.REQUEST_TIMEOUT;
            summary = "模型请求超时";
        } else if (exception instanceof RunExecutionException runException) {
            errorCode = runException.errorCode;
            summary = runException.getMessage();
        }
        ErrorCode finalErrorCode = errorCode;
        String finalSummary = sanitize(summary);
        RunEventRecord event = transactionTemplate.execute(status -> {
            AgentRunRecord latest = requireRunForFinalization(run.getId());
            if (isTerminal(latest.getStatus())) {
                return null;
            }
            if ("CANCELLING".equals(latest.getStatus())) {
                return finalizeCancelledLocked(run, call, latest.getStatus());
            }
            if ("PAUSING".equals(latest.getStatus())) {
                return finalizePausedLocked(run, call, latest.getStatus());
            }
            if (!List.of("QUEUED", "RUNNING", "WAITING_USER")
                    .contains(latest.getStatus())) {
                throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                        "运行状态已变化，不能写入失败状态");
            }
            return finalizeFailedLocked(
                    run,
                    call,
                    latest.getStatus(),
                    finalErrorCode,
                    finalSummary);
        });
        if (event != null) {
            sseHub.publish(event);
        }
    }

    private AgentRunRecord requireRunForFinalization(Long runId) {
        AgentRunRecord latest = agentRunMapper.findByIdForUpdate(runId);
        if (latest == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    ErrorCode.RESOURCE_NOT_FOUND,
                    "运行不存在");
        }
        return latest;
    }

    private RunEventRecord finalizeCancelledLocked(AgentRunRecord run,
                                                   ModelCallRecord call,
                                                   String fromStatus) {
        if (call != null) {
            modelCallMapper.updateFinished(call.getId(), "CANCELLED", Instant.now(),
                    null, null, null, ErrorCode.CANCELLED.name(), "运行已取消");
        }
        if (agentRunMapper.updateStatus(run.getId(), fromStatus, "CANCELLED",
                ErrorCode.CANCELLED.name(), "运行已取消", Instant.now()) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "运行状态已变化，不能完成取消");
        }
        taskService.updateRunStatus(run.getTaskId(), run.getId(), "CANCELLED");
        return runEventService.append(run.getId(), call == null ? null : call.getId(),
                "run.cancelled", "运行已取消", Map.of("status", "CANCELLED"));
    }

    private RunEventRecord finalizePausedLocked(AgentRunRecord run,
                                                ModelCallRecord call,
                                                String fromStatus) {
        if (call != null) {
            modelCallMapper.updateFinished(call.getId(), "PAUSED", Instant.now(),
                    null, null, null, null, null);
        }
        if (agentRunMapper.updateStatus(run.getId(), fromStatus, "PAUSED",
                null, null, null) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "运行状态已变化，不能完成暂停");
        }
        taskService.updateRunStatus(run.getTaskId(), run.getId(), "PAUSED");
        return runEventService.append(run.getId(), call == null ? null : call.getId(),
                "run.paused", "运行已暂停", Map.of("status", "PAUSED"));
    }

    private RunEventRecord finalizeFailedLocked(AgentRunRecord run,
                                                ModelCallRecord call,
                                                String fromStatus,
                                                ErrorCode errorCode,
                                                String summary) {
        if (call != null) {
            modelCallMapper.updateFinished(call.getId(), "FAILED", Instant.now(),
                    null, null, null, errorCode.name(), summary);
        }
        if (agentRunMapper.updateStatus(run.getId(), fromStatus, "FAILED",
                errorCode.name(), summary, Instant.now()) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "运行状态已变化，不能写入失败状态");
        }
        taskService.updateRunStatus(run.getTaskId(), run.getId(), "FAILED");
        return runEventService.append(run.getId(), call == null ? null : call.getId(),
                "run.failed", summary,
                Map.of("status", "FAILED", "errorCode", errorCode.name()));
    }

    private boolean isTerminal(String status) {
        return List.of(
                        "SUCCEEDED",
                        "FAILED",
                        "CANCELLED",
                        "PAUSED",
                        "RECOVERY_REQUIRED")
                .contains(status);
    }

    private Disposable startHeartbeat(Long runId) {
        Duration interval = heartbeatInterval();
        return Flux.interval(interval, interval, Schedulers.boundedElastic())
                .subscribe(ignored -> {
                    try {
                        agentRunMapper.touchHeartbeat(runId, Instant.now());
                    } catch (RuntimeException exception) {
                        LOGGER.warn("Failed to update heartbeat for agent run {}", runId);
                    }
                });
    }

    private Duration maxRunDuration() {
        Duration configured = runtimeProperties.getMaxRunDuration();
        return configured == null || configured.isZero() || configured.isNegative()
                ? Duration.ofMinutes(5)
                : configured;
    }

    private Duration heartbeatInterval() {
        Duration configured = runtimeProperties.getHeartbeatInterval();
        return configured == null || configured.isZero() || configured.isNegative()
                ? Duration.ofSeconds(10)
                : configured;
    }

    private Throwable normalizeExecutionFailure(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof RunExecutionException
                    || current instanceof AiProviderException
                    || current instanceof ApiException) {
                return current;
            }
            if (current instanceof TimeoutException) {
                return new RunExecutionException(
                        ErrorCode.REQUEST_TIMEOUT,
                        "模型请求超过最大运行时长");
            }
            current = current.getCause();
        }
        return exception;
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "运行失败";
        }
        String normalized = value.replaceAll("(?i)(authorization|api[-_ ]?key|token)\\s*[:=]\\s*[^,\\s]+",
                "$1=[REDACTED]");
        return normalized.length() > 512 ? normalized.substring(0, 512) : normalized;
    }

    private void clearSecret(AiConnectionRuntimeConfig connection) {
        // The immutable runtime record is eligible for collection after this method returns.
    }

    private String requireRunPrompt(AgentRunRecord run) {
        return run.getAgentVersionId() == null
                ? agentDefinitionService.requirePrompt(run.getAgentDefinitionId())
                : agentDefinitionService.requirePrompt(
                        run.getAgentVersionId(), run.getAgentDefinitionId());
    }

    private static final class StreamBudget {
        private final long maxCharacters;
        private final long maxBytes;
        private final long maxEvents;
        private final long maxDurationNanos;
        private final long startedAtNanos = System.nanoTime();
        private long characters;
        private long bytes;
        private long events;

        private StreamBudget(AgentRuntimeProperties properties) {
            this.maxCharacters = Math.max(1, properties.getMaxOutputCharacters());
            this.maxBytes = Math.max(1, properties.getMaxOutputBytes());
            this.maxEvents = Math.max(1, properties.getMaxStreamEvents());
            Duration duration = properties.getMaxRunDuration();
            Duration normalized = duration == null || duration.isZero() || duration.isNegative()
                    ? Duration.ofMinutes(5)
                    : duration;
            this.maxDurationNanos = normalized.toNanos();
        }

        private void accept(AiStreamEvent event) {
            if (System.nanoTime() - startedAtNanos > maxDurationNanos) {
                throw new RunExecutionException(
                        ErrorCode.REQUEST_TIMEOUT,
                        "模型请求超过最大运行时长");
            }
            events++;
            if (events > maxEvents) {
                throw new RunExecutionException(
                        ErrorCode.OUTPUT_LIMIT_EXCEEDED,
                        "模型流事件数量超过运行上限");
            }
            if (event.text() == null || event.text().isEmpty()) {
                return;
            }
            characters += event.text().length();
            bytes += event.text().getBytes(StandardCharsets.UTF_8).length;
            if (characters > maxCharacters || bytes > maxBytes) {
                throw new RunExecutionException(
                        ErrorCode.OUTPUT_LIMIT_EXCEEDED,
                        "模型输出内容超过运行上限");
            }
        }
    }

    private static final class RunExecutionException extends RuntimeException {
        private final ErrorCode errorCode;

        private RunExecutionException(ErrorCode errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }
    }
}
