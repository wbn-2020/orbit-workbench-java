package com.orbitworkbench.tool.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.agent.application.AgentRunStepService;
import com.orbitworkbench.agent.application.RunEventService;
import com.orbitworkbench.agent.application.SseHub;
import com.orbitworkbench.ai.application.AiToolCall;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.shared.config.ToolRuntimeProperties;
import com.orbitworkbench.storage.application.LocalStorageService;
import com.orbitworkbench.storage.domain.StoredFile;
import com.orbitworkbench.tool.domain.ToolCallRecord;
import com.orbitworkbench.tool.domain.ToolVersionRecord;
import com.orbitworkbench.tool.infrastructure.mapper.ToolCallMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ToolExecutionService {

    private final ToolRegistry registry;
    private final ToolCallMapper callMapper;
    private final AgentRunStepService stepService;
    private final JsonSchemaValidator schemaValidator;
    private final LocalStorageService storageService;
    private final ObjectMapper objectMapper;
    private final RunEventService eventService;
    private final SseHub sseHub;
    private final ThreadPoolTaskExecutor toolTaskExecutor;
    private final TransactionTemplate transactionTemplate;
    private final ToolRuntimeProperties properties;

    public ToolExecutionService(
            ToolRegistry registry,
            ToolCallMapper callMapper,
            AgentRunStepService stepService,
            JsonSchemaValidator schemaValidator,
            LocalStorageService storageService,
            ObjectMapper objectMapper,
            RunEventService eventService,
            SseHub sseHub,
            @Qualifier("toolTaskExecutor") ThreadPoolTaskExecutor toolTaskExecutor,
            TransactionTemplate transactionTemplate,
            ToolRuntimeProperties properties) {
        this.registry = registry;
        this.callMapper = callMapper;
        this.stepService = stepService;
        this.schemaValidator = schemaValidator;
        this.storageService = storageService;
        this.objectMapper = objectMapper;
        this.eventService = eventService;
        this.sseHub = sseHub;
        this.toolTaskExecutor = toolTaskExecutor;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
    }

    public ToolExecutionOutcome execute(ToolExecutionContext context,
                                        AiToolCall requestedCall) {
        if (requestedCall == null || requestedCall.id() == null
                || requestedCall.id().isBlank()
                || requestedCall.name() == null
                || requestedCall.name().isBlank()) {
            throw invalid("ToolCall 缺少 id 或 name");
        }
        byte[] argumentBytes = normalizedArguments(requestedCall.argumentsJson());
        if (argumentBytes.length > Math.max(1, properties.getMaxArgumentsBytes())) {
            throw invalid("ToolCall 参数超过大小限制");
        }
        JsonNode arguments = readObject(argumentBytes);
        Long scopedVersionId = context.enforceToolScope()
                ? context.toolVersionIds().get(requestedCall.name()) : null;
        if (context.enforceToolScope() && scopedVersionId == null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.TOOL_DISABLED,
                    "当前 Agent 未授权该工具");
        }
        ToolRegistry.RegisteredTool registered = context.enforceToolScope()
                ? registry.require(requestedCall.name(), scopedVersionId)
                : registry.require(requestedCall.name());
        ToolVersionRecord version = registered.version();
        schemaValidator.validate(readSchema(version), arguments);

        String argumentsHash = sha256(argumentBytes);
        String callKey = "tc_" + sha256((context.runId() + "|"
                + requestedCall.id()).getBytes(StandardCharsets.UTF_8));
        ToolCallRecord existing = callMapper.findByCallKey(callKey);
        if (existing != null) {
            return replay(existing, requestedCall.name(), version, argumentsHash);
        }

        StoredFile argumentsSnapshot = storageService.storeToolSnapshot(
                new ByteArrayInputStream(argumentBytes),
                properties.getMaxArgumentsBytes());
        ToolCallRecord call = newCall(
                context, requestedCall, version, argumentsHash,
                callKey, argumentsSnapshot.storageRef());
        try {
            transactionTemplate.executeWithoutResult(status -> {
                callMapper.insert(call);
                stepService.attachToolCall(context.stepId(), call.getId());
                publishAfterCommit(context, call, "tool.call.requested",
                        "工具调用已请求",
                        Map.of("toolCallId", call.getId(),
                                "toolCode", call.getToolCode()));
            });
        } catch (DuplicateKeyException exception) {
            storageService.moveToTrash(argumentsSnapshot.storageRef());
            ToolCallRecord raced = callMapper.findByCallKey(callKey);
            if (raced != null) {
                return replay(raced, requestedCall.name(), version, argumentsHash);
            }
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.TOOL_CALL_CONFLICT,
                    "工具调用幂等状态冲突");
        }

        markRunning(context, call);
        Future<ToolExecutionResult> future;
        try {
            future = toolTaskExecutor.submit(
                    () -> registered.handler().execute(context, arguments));
        } catch (RejectedExecutionException exception) {
            fail(context, call, ErrorCode.UPSTREAM_UNAVAILABLE, "工具执行队列已满");
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    ErrorCode.UPSTREAM_UNAVAILABLE,
                    "工具执行队列已满");
        }

        try {
            ToolExecutionResult result =
                    waitForResult(future, timeout(version), context);
            schemaValidator.validate(readOutputSchema(version), result.content());
            byte[] resultBytes = writeResult(result.content());
            int resultLimit = Math.min(
                    Math.max(1, properties.getMaxResultBytes()),
                    Math.max(1, version.getMaxResultBytes() == null
                            ? properties.getMaxResultBytes()
                            : version.getMaxResultBytes()));
            if (resultBytes.length > resultLimit) {
                throw new ApiException(
                        HttpStatus.PAYLOAD_TOO_LARGE,
                        ErrorCode.TOOL_RESULT_TOO_LARGE,
                        "工具结果超过大小限制");
            }
            StoredFile resultSnapshot = storageService.storeToolSnapshot(
                    new ByteArrayInputStream(resultBytes),
                    resultLimit);
            try {
                complete(context, call, resultSnapshot, result.summary());
            } catch (RuntimeException exception) {
                try {
                    storageService.moveToTrash(resultSnapshot.storageRef());
                } catch (RuntimeException cleanupException) {
                    exception.addSuppressed(cleanupException);
                }
                throw exception;
            }
            return new ToolExecutionOutcome(callMapper.findById(call.getId()), result.content());
        } catch (TimeoutException exception) {
            future.cancel(true);
            fail(context, call, ErrorCode.TOOL_TIMEOUT, "工具执行超时");
            throw new ApiException(
                    HttpStatus.GATEWAY_TIMEOUT,
                    ErrorCode.TOOL_TIMEOUT,
                    "工具执行超时");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            fail(context, call, ErrorCode.CANCELLED, "工具执行已取消");
            throw new ApiException(
                    HttpStatus.REQUEST_TIMEOUT,
                    ErrorCode.CANCELLED,
                    "工具执行已取消");
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof ApiException apiException) {
                fail(context, call, apiException.getErrorCode(), apiException.getMessage());
                throw apiException;
            }
            fail(context, call, ErrorCode.TOOL_EXECUTION_FAILED, "工具执行失败");
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    ErrorCode.TOOL_EXECUTION_FAILED,
                    "工具执行失败");
        } catch (ApiException exception) {
            fail(context, call, exception.getErrorCode(), exception.getMessage());
            throw exception;
        } catch (RuntimeException exception) {
            future.cancel(true);
            cancel(context, call);
            throw exception;
        }
    }

    private ToolExecutionResult waitForResult(
            Future<ToolExecutionResult> future,
            Duration timeout,
            ToolExecutionContext context)
            throws InterruptedException, ExecutionException, TimeoutException {
        long timeoutNanos = timeout.toNanos();
        long startedAt = System.nanoTime();
        while (true) {
            context.checkControl();
            long elapsed = System.nanoTime() - startedAt;
            long remaining = timeoutNanos - elapsed;
            if (remaining <= 0) {
                throw new TimeoutException("tool timeout");
            }
            try {
                return future.get(
                        Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(250)),
                        TimeUnit.NANOSECONDS);
            } catch (TimeoutException exception) {
                if (System.nanoTime() - startedAt >= timeoutNanos) {
                    throw exception;
                }
            }
        }
    }

    private ToolExecutionOutcome replay(ToolCallRecord existing,
                                        String toolCode,
                                        ToolVersionRecord version,
                                        String argumentsHash) {
        if (!toolCode.equals(existing.getToolCode())
                || !version.getId().equals(existing.getToolVersionId())
                || !argumentsHash.equals(existing.getArgumentsHash())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.TOOL_CALL_CONFLICT,
                    "ToolCall id 已用于其他参数");
        }
        if (!"SUCCEEDED".equals(existing.getStatus())
                || existing.getResultSnapshotRef() == null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.TOOL_CALL_CONFLICT,
                    "工具调用尚未形成可重放结果");
        }
        try {
            JsonNode result = objectMapper.readTree(
                    storageService.readUtf8(existing.getResultSnapshotRef()));
            return new ToolExecutionOutcome(existing, result);
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.TOOL_EXECUTION_FAILED,
                    "工具结果快照无法读取");
        }
    }

    private ToolCallRecord newCall(ToolExecutionContext context,
                                   AiToolCall requestedCall,
                                   ToolVersionRecord version,
                                   String argumentsHash,
                                   String callKey,
                                   String argumentsSnapshotRef) {
        Instant now = Instant.now();
        ToolCallRecord call = new ToolCallRecord();
        call.setAgentRunId(context.runId());
        call.setStepId(context.stepId());
        call.setModelCallId(context.modelCallId());
        call.setToolDefinitionId(version.getLegacyToolDefinitionId());
        call.setToolVersionId(version.getId());
        call.setToolCode(version.getToolCode());
        call.setToolVersion(version.getVersionNumber());
        call.setCallKey(callKey);
        call.setArgumentsHash(argumentsHash);
        call.setStatus("PENDING");
        call.setArgumentsSnapshotRef(argumentsSnapshotRef);
        call.setArgumentsSummary("已校验 " + requestedCall.name() + " 参数");
        call.setCreatedAt(now);
        call.setUpdatedAt(now);
        return call;
    }

    private void markRunning(ToolExecutionContext context, ToolCallRecord call) {
        transactionTemplate.executeWithoutResult(status -> {
            Instant now = Instant.now();
            if (callMapper.markRunning(call.getId(), now) != 1) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        ErrorCode.TOOL_CALL_CONFLICT,
                        "工具调用状态已变化");
            }
            call.setStatus("RUNNING");
            call.setStartedAt(now);
            publishAfterCommit(context, call, "tool.call.started",
                    "工具调用已开始",
                    Map.of("toolCallId", call.getId(),
                            "toolCode", call.getToolCode()));
        });
    }

    private void complete(ToolExecutionContext context,
                          ToolCallRecord call,
                          StoredFile snapshot,
                          String summary) {
        transactionTemplate.executeWithoutResult(status -> {
            Instant now = Instant.now();
            String safeSummary = safeSummary(summary, "工具调用已完成");
            if (callMapper.markSucceeded(
                    call.getId(),
                    snapshot.storageRef(),
                    safeSummary,
                    Math.toIntExact(snapshot.sizeBytes()),
                    now) != 1) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        ErrorCode.TOOL_CALL_CONFLICT,
                        "工具调用状态已变化");
            }
            publishAfterCommit(context, call, "tool.call.completed",
                    safeSummary,
                    Map.of("toolCallId", call.getId(),
                            "toolCode", call.getToolCode(),
                            "resultSizeBytes", snapshot.sizeBytes()));
        });
    }

    private void fail(ToolExecutionContext context,
                      ToolCallRecord call,
                      ErrorCode errorCode,
                      String summary) {
        transactionTemplate.executeWithoutResult(status -> {
            Instant now = Instant.now();
            String safeSummary = safeSummary(summary, "工具调用失败");
            if (callMapper.markFailed(
                    call.getId(), errorCode.name(), safeSummary, now) == 1) {
                publishAfterCommit(context, call, "tool.call.failed",
                        safeSummary,
                        Map.of("toolCallId", call.getId(),
                                "toolCode", call.getToolCode(),
                                "errorCode", errorCode.name()));
            }
        });
    }

    private void cancel(ToolExecutionContext context, ToolCallRecord call) {
        transactionTemplate.executeWithoutResult(status -> {
            Instant now = Instant.now();
            if (callMapper.markCancelled(call.getId(), now) == 1) {
                publishAfterCommit(context, call, "tool.call.cancelled",
                        "工具调用已取消",
                        Map.of("toolCallId", call.getId(),
                                "toolCode", call.getToolCode(),
                                "errorCode", ErrorCode.CANCELLED.name()));
            }
        });
    }

    private void publishAfterCommit(ToolExecutionContext context,
                                    ToolCallRecord call,
                                    String type,
                                    String summary,
                                    Object payload) {
        var event = eventService.append(
                context.runId(),
                call.getModelCallId(),
                type,
                summary,
                payload);
        sseHub.publishAfterCommit(event);
    }

    private JsonNode readSchema(ToolVersionRecord version) {
        try {
            return objectMapper.readTree(version.getInputSchemaJson());
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.TOOL_DISABLED,
                    "工具参数 Schema 无法读取");
        }
    }

    private JsonNode readOutputSchema(ToolVersionRecord version) {
        try {
            return objectMapper.readTree(version.getOutputSchemaJson());
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.TOOL_DISABLED,
                    "工具结果 Schema 无法读取");
        }
    }

    private byte[] normalizedArguments(String value) {
        String normalized = value == null || value.isBlank() ? "{}" : value;
        return normalized.getBytes(StandardCharsets.UTF_8);
    }

    private JsonNode readObject(byte[] value) {
        try {
            JsonNode node = objectMapper.readTree(
                    new String(value, StandardCharsets.UTF_8));
            if (node == null || !node.isObject()) {
                throw invalid("ToolCall 参数必须是 JSON 对象");
            }
            return node;
        } catch (JsonProcessingException exception) {
            throw invalid("ToolCall 参数不是有效 JSON");
        }
    }

    private byte[] writeResult(JsonNode value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    ErrorCode.TOOL_EXECUTION_FAILED,
                    "工具结果无法序列化");
        }
    }

    private Duration timeout(ToolVersionRecord version) {
        int versionTimeout = version.getTimeoutMs() == null
                ? Integer.MAX_VALUE : version.getTimeoutMs();
        Duration configured = properties.getDefaultTimeout();
        long configuredMillis = configured == null
                || configured.isZero()
                || configured.isNegative()
                ? 30_000L : configured.toMillis();
        return Duration.ofMillis(Math.max(
                1L, Math.min(versionTimeout, configuredMillis)));
    }

    private String safeSummary(String value, String fallback) {
        String normalized = value == null || value.isBlank()
                ? fallback : value.replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .trim();
        return normalized.length() <= 512
                ? normalized : normalized.substring(0, 512);
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private ApiException invalid(String message) {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                ErrorCode.TOOL_CALL_INVALID,
                message);
    }
}
