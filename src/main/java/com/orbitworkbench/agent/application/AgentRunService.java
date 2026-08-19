package com.orbitworkbench.agent.application;

import com.orbitworkbench.agent.api.AgentRunDtos.AgentRunResponse;
import com.orbitworkbench.agent.api.AgentRunDtos.ModelCallResponse;
import com.orbitworkbench.agent.api.AgentRunDtos.RunCreatedResponse;
import com.orbitworkbench.agent.domain.AgentRunRecord;
import com.orbitworkbench.agent.domain.ModelCallRecord;
import com.orbitworkbench.agent.domain.RunEventRecord;
import com.orbitworkbench.agent.infrastructure.mapper.AgentRunMapper;
import com.orbitworkbench.agent.infrastructure.mapper.ModelCallMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.task.application.TaskService;
import com.orbitworkbench.task.domain.TaskRecord;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AgentRunService {

    private static final List<String> ACTIVE_RUN_STATUSES = List.of(
            "QUEUED", "RUNNING", "WAITING_USER", "PAUSING", "PAUSED", "CANCELLING");

    private final AgentRunMapper agentRunMapper;
    private final ModelCallMapper modelCallMapper;
    private final AgentDefinitionService agentDefinitionService;
    private final TaskService taskService;
    private final RunEventService runEventService;
    private final SseHub sseHub;
    private final ThreadPoolTaskExecutor agentTaskExecutor;
    private final AgentRunWorker agentRunWorker;
    private final TransactionTemplate transactionTemplate;

    public AgentRunService(AgentRunMapper agentRunMapper,
                           ModelCallMapper modelCallMapper,
                           AgentDefinitionService agentDefinitionService,
                           TaskService taskService,
                           RunEventService runEventService,
                           SseHub sseHub,
                           ThreadPoolTaskExecutor agentTaskExecutor,
                           AgentRunWorker agentRunWorker,
                           TransactionTemplate transactionTemplate) {
        this.agentRunMapper = agentRunMapper;
        this.modelCallMapper = modelCallMapper;
        this.agentDefinitionService = agentDefinitionService;
        this.taskService = taskService;
        this.runEventService = runEventService;
        this.sseHub = sseHub;
        this.agentTaskExecutor = agentTaskExecutor;
        this.agentRunWorker = agentRunWorker;
        this.transactionTemplate = transactionTemplate;
    }

    @Transactional
    public RunCreatedResponse start(Long taskId, Long retryOfRunId) {
        TaskRecord currentTask = taskService.lockForRun(taskId);
        if (retryOfRunId == null && !"READY".equals(currentTask.getStatus())) {
            AgentRunRecord currentRun = findActiveCurrentRun(currentTask);
            if (currentRun != null) {
                return toCreatedResponse(currentRun, currentTask.getStatus(), "START");
            }
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "当前任务状态不允许启动运行");
        }
        java.util.Set<String> allowedTaskStatuses = retryOfRunId == null
                ? java.util.Set.of("READY")
                : java.util.Set.of("FAILED", "CANCELLED");
        return createRun(currentTask, retryOfRunId, allowedTaskStatuses, "START");
    }

    private RunCreatedResponse createRun(TaskRecord lockedTask,
                                         Long retryOfRunId,
                                         java.util.Set<String> allowedTaskStatuses,
                                         String command) {
        taskService.validateLockedRunInput(lockedTask);
        AgentRunRecord run = new AgentRunRecord();
        run.setTaskId(lockedTask.getId());
        run.setAgentDefinitionId(
                agentDefinitionService.requireActiveId(lockedTask.getModuleType()));
        run.setConnectionId(lockedTask.getConnectionId());
        run.setStatus("QUEUED");
        run.setRetryOfRunId(retryOfRunId);
        run.setTraceId(MDC.get("traceId") == null
                ? UUID.randomUUID().toString().replace("-", "")
                : MDC.get("traceId"));
        Instant now = Instant.now();
        run.setCreatedAt(now);
        run.setUpdatedAt(now);
        agentRunMapper.insert(run);
        taskService.attachRun(lockedTask, run.getId(), allowedTaskStatuses);
        RunEventRecord event = runEventService.append(run.getId(), null, "run.started",
                "运行已排队", java.util.Map.of("status", "QUEUED"));
        sseHub.publishAfterCommit(event);
        submitAfterCommit(run.getId());
        return new RunCreatedResponse(
                run.getId(), "QUEUED", "RUNNING", command, retryOfRunId);
    }

    @Transactional(readOnly = true)
    public AgentRunResponse get(Long id) {
        return toResponse(requireRun(id), true);
    }

    @Transactional(readOnly = true)
    public List<AgentRunResponse> listByTask(Long taskId) {
        taskService.requireTask(taskId);
        return agentRunMapper.findByTaskId(taskId).stream()
                .map(run -> toResponse(run, false))
                .toList();
    }

    @Transactional
    public AgentRunResponse cancel(Long id) {
        AgentRunRecord run = requireRunForUpdate(id);
        if (List.of("CANCELLING", "CANCELLED").contains(run.getStatus())) {
            return toResponse(run, true);
        }
        if (!List.of("QUEUED", "RUNNING", "WAITING_USER", "PAUSED", "PAUSING")
                .contains(run.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "当前运行状态不允许取消");
        }
        boolean immediate = List.of("QUEUED", "WAITING_USER", "PAUSED")
                .contains(run.getStatus());
        int updated = immediate
                ? agentRunMapper.updateStatus(id, run.getStatus(), "CANCELLED",
                ErrorCode.CANCELLED.name(), "运行已取消", Instant.now())
                : agentRunMapper.requestCancel(id, List.of("RUNNING", "PAUSING"));
        if (updated != 1) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "运行状态已变化，请刷新后重试");
        }
        if (immediate) {
            taskService.updateRunStatus(run.getTaskId(), id, "CANCELLED");
        }
        String targetStatus = immediate ? "CANCELLED" : "CANCELLING";
        String eventType = immediate ? "run.cancelled" : "run.cancel.requested";
        RunEventRecord event = runEventService.append(id, null, eventType,
                immediate ? "运行已取消" : "已请求取消运行",
                java.util.Map.of("status", targetStatus));
        sseHub.publishAfterCommit(event);
        return get(id);
    }

    @Transactional
    public AgentRunResponse pause(Long id) {
        AgentRunRecord run = requireRunForUpdate(id);
        if (List.of("PAUSING", "PAUSED").contains(run.getStatus())) {
            return toResponse(run, true);
        }
        if (!List.of("RUNNING", "QUEUED").contains(run.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "当前运行状态不允许暂停");
        }
        String targetStatus = "PAUSING";
        if (agentRunMapper.updateStatus(id, run.getStatus(), targetStatus, null, null, null) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "运行状态已变化，请刷新后重试");
        }
        RunEventRecord event = runEventService.append(id, null, "run.pause.requested",
                "已请求暂停运行",
                java.util.Map.of("status", targetStatus));
        sseHub.publishAfterCommit(event);
        return get(id);
    }

    @Transactional
    public AgentRunResponse resume(Long id) {
        AgentRunRecord run = requireRunForUpdate(id);
        AgentRunRecord existingRetry = agentRunMapper.findLatestRetryByRunId(id);
        if (existingRetry != null) {
            return toResponse(existingRetry, true);
        }
        if (!"PAUSED".equals(run.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "当前运行不是暂停状态");
        }
        TaskRecord task = taskService.lockForRun(run.getTaskId());
        RunCreatedResponse created = createRun(
                task, id, java.util.Set.of("PAUSED"), "RESUME");
        RunEventRecord event = runEventService.append(id, null, "run.resumed",
                "已创建恢复运行",
                java.util.Map.of(
                        "status", "PAUSED",
                        "successorRunId", created.runId()));
        sseHub.publishAfterCommit(event);
        return get(created.runId());
    }

    @Transactional
    public AgentRunResponse retry(Long id) {
        AgentRunRecord run = requireRunForUpdate(id);
        if (!List.of("FAILED", "CANCELLED", "RECOVERY_REQUIRED").contains(run.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "当前运行状态不允许重试");
        }
        AgentRunRecord existingRetry = agentRunMapper.findLatestRetryByRunId(id);
        if (existingRetry != null) {
            return toResponse(existingRetry, true);
        }
        TaskRecord task = taskService.lockForRun(run.getTaskId());
        RunCreatedResponse created = createRun(
                task, id, java.util.Set.of("FAILED", "CANCELLED"), "RETRY");
        return get(created.runId());
    }

    @Transactional
    public void recoverInterruptedRuns() {
        for (Long id : agentRunMapper.findInterruptedIds()) {
            if (agentRunMapper.markRecoveryRequired(id) == 1) {
                Instant recoveredAt = Instant.now();
                modelCallMapper.finishRunningByRunId(
                        id,
                        "FAILED",
                        recoveredAt,
                        ErrorCode.STREAM_INTERRUPTED.name(),
                        "服务重启导致模型调用中断");
                AgentRunRecord run = agentRunMapper.findById(id);
                if (run != null) {
                    taskService.updateRunStatus(run.getTaskId(), id, "FAILED");
                }
                RunEventRecord event = runEventService.append(id, null, "run.failed",
                        "服务重启后运行需要恢复", java.util.Map.of("status", "RECOVERY_REQUIRED"));
                sseHub.publishAfterCommit(event);
            }
        }
    }

    public RunEventService events() {
        return runEventService;
    }

    public SseHub sseHub() {
        return sseHub;
    }

    private void submitAfterCommit(Long runId) {
        Runnable submit = () -> {
            try {
                agentTaskExecutor.execute(() -> agentRunWorker.execute(runId));
            } catch (RejectedExecutionException exception) {
                markSubmissionFailure(runId);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submit.run();
                }
            });
        } else {
            submit.run();
        }
    }

    private void markSubmissionFailure(Long runId) {
        RunEventRecord event = transactionTemplate.execute(status -> {
            AgentRunRecord run = agentRunMapper.findByIdForUpdate(runId);
            if (run == null) {
                return null;
            }
            if ("PAUSING".equals(run.getStatus())) {
                if (agentRunMapper.updateStatus(
                        runId, "PAUSING", "PAUSED", null, null, null) != 1) {
                    return null;
                }
                taskService.updateRunStatus(run.getTaskId(), runId, "PAUSED");
                return runEventService.append(runId, null, "run.paused",
                        "运行在进入执行队列前已暂停",
                        java.util.Map.of("status", "PAUSED"));
            }
            if ("CANCELLING".equals(run.getStatus())) {
                if (agentRunMapper.updateStatus(
                        runId,
                        "CANCELLING",
                        "CANCELLED",
                        ErrorCode.CANCELLED.name(),
                        "运行已取消",
                        Instant.now()) != 1) {
                    return null;
                }
                taskService.updateRunStatus(run.getTaskId(), runId, "CANCELLED");
                return runEventService.append(runId, null, "run.cancelled",
                        "运行已取消",
                        java.util.Map.of("status", "CANCELLED"));
            }
            if (!"QUEUED".equals(run.getStatus())
                    || agentRunMapper.updateStatus(
                    runId,
                    "QUEUED",
                    "FAILED",
                    ErrorCode.UPSTREAM_UNAVAILABLE.name(),
                    "运行队列已满，未能提交后台执行",
                    Instant.now()) != 1) {
                return null;
            }
            taskService.updateRunStatus(run.getTaskId(), runId, "FAILED");
            return runEventService.append(runId, null, "run.failed",
                    "运行队列已满，未能提交后台执行",
                    java.util.Map.of("status", "FAILED",
                            "errorCode", ErrorCode.UPSTREAM_UNAVAILABLE.name()));
        });
        if (event != null) {
            sseHub.publish(event);
        }
    }

    private AgentRunRecord requireRun(Long id) {
        AgentRunRecord run = agentRunMapper.findById(id);
        if (run == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "运行不存在");
        }
        return run;
    }

    private AgentRunRecord requireRunForUpdate(Long id) {
        AgentRunRecord run = agentRunMapper.findByIdForUpdate(id);
        if (run == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "运行不存在");
        }
        return run;
    }

    private AgentRunRecord findActiveCurrentRun(TaskRecord task) {
        if (task.getCurrentRunId() == null) {
            return null;
        }
        AgentRunRecord run = agentRunMapper.findById(task.getCurrentRunId());
        return run != null && ACTIVE_RUN_STATUSES.contains(run.getStatus())
                ? run
                : null;
    }

    private RunCreatedResponse toCreatedResponse(AgentRunRecord run,
                                                 String taskStatus,
                                                 String command) {
        return new RunCreatedResponse(
                run.getId(),
                run.getStatus(),
                taskStatus,
                command,
                run.getRetryOfRunId());
    }

    private AgentRunResponse toResponse(AgentRunRecord run, boolean includeDetails) {
        Long lastSequence = includeDetails
                ? runEventService.latestSequence(run.getId())
                : null;
        List<ModelCallResponse> modelCalls = includeDetails
                ? modelCallMapper.findByRunId(run.getId()).stream()
                .map(this::toModelCallResponse)
                .toList()
                : null;
        AgentRunRecord successor = includeDetails
                ? agentRunMapper.findLatestRetryByRunId(run.getId())
                : null;
        return new AgentRunResponse(
                run.getId(),
                run.getTaskId(),
                run.getAgentDefinitionId(),
                run.getConnectionId(),
                run.getStatus(),
                run.getCurrentStep(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getLastHeartbeatAt(),
                run.getRetryOfRunId(),
                successor == null ? null : successor.getId(),
                run.getErrorCode(),
                run.getErrorSummary(),
                run.getTraceId(),
                run.getCreatedAt(),
                run.getUpdatedAt(),
                lastSequence,
                modelCalls
        );
    }

    private ModelCallResponse toModelCallResponse(ModelCallRecord call) {
        Long latencyMs = call.getStartedAt() == null || call.getFinishedAt() == null
                ? null
                : Math.max(0L, Duration.between(
                        call.getStartedAt(), call.getFinishedAt()).toMillis());
        return new ModelCallResponse(
                call.getId(),
                call.getConnectionId(),
                call.getConnectionNameSnapshot(),
                call.getModelNameSnapshot(),
                call.getProtocol(),
                call.isStreaming(),
                call.getStatus(),
                latencyMs,
                call.getInputTokenCount(),
                call.getOutputTokenCount(),
                call.getProviderRequestId(),
                call.getErrorCode(),
                call.getErrorSummary(),
                call.getStartedAt(),
                call.getFinishedAt()
        );
    }
}
