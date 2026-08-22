package com.orbitworkbench.agent.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.agent.domain.AgentRunRecord;
import com.orbitworkbench.agent.domain.AgentRunStepRecord;
import com.orbitworkbench.agent.domain.AgentVersionRecord;
import com.orbitworkbench.agent.domain.ModelCallRecord;
import com.orbitworkbench.agent.domain.RunEventRecord;
import com.orbitworkbench.agent.infrastructure.mapper.AgentVersionMapper;
import com.orbitworkbench.agent.infrastructure.mapper.ModelCallMapper;
import com.orbitworkbench.ai.application.AiConversationItem;
import com.orbitworkbench.ai.application.AiInvocation;
import com.orbitworkbench.ai.application.AiProviderException;
import com.orbitworkbench.ai.application.AiStreamEvent;
import com.orbitworkbench.ai.application.AiToolCall;
import com.orbitworkbench.ai.application.AiUsage;
import com.orbitworkbench.ai.application.ModelGateway;
import com.orbitworkbench.aiconnection.application.AiConnectionService.AiConnectionRuntimeConfig;
import com.orbitworkbench.analysis.application.DataAnalysisRunContext;
import com.orbitworkbench.analysis.application.DataAnalysisTaskService;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.shared.config.AgentRuntimeProperties;
import com.orbitworkbench.shared.config.ToolRuntimeProperties;
import com.orbitworkbench.skill.domain.SkillDefinitionRecord;
import com.orbitworkbench.skill.domain.SkillVersionRecord;
import com.orbitworkbench.skill.infrastructure.mapper.SkillDefinitionMapper;
import com.orbitworkbench.skill.infrastructure.mapper.SkillVersionMapper;
import com.orbitworkbench.task.domain.TaskRecord;
import com.orbitworkbench.tool.application.ToolExecutionContext;
import com.orbitworkbench.tool.application.ToolExecutionOutcome;
import com.orbitworkbench.tool.application.ToolExecutionService;
import com.orbitworkbench.tool.application.ToolRegistry;
import com.orbitworkbench.tool.domain.ToolVersionRecord;
import com.orbitworkbench.tool.infrastructure.mapper.SkillVersionToolMapper;
import com.orbitworkbench.tool.infrastructure.mapper.ToolVersionMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class DataAnalysisAgentRunner {

    private static final int MAX_OUTPUT_TOKENS = 4096;

    private final DataAnalysisTaskService analysisTaskService;
    private final AgentRunStepService stepService;
    private final ModelCallMapper modelCallMapper;
    private final AgentVersionMapper agentVersionMapper;
    private final SkillVersionMapper skillVersionMapper;
    private final SkillDefinitionMapper skillDefinitionMapper;
    private final SkillVersionToolMapper skillVersionToolMapper;
    private final ToolVersionMapper toolVersionMapper;
    private final ModelGateway modelGateway;
    private final ToolRegistry toolRegistry;
    private final ToolExecutionService toolExecutionService;
    private final RunEventService eventService;
    private final SseHub sseHub;
    private final ObjectMapper objectMapper;
    private final ToolRuntimeProperties toolProperties;
    private final AgentRuntimeProperties runtimeProperties;

    public DataAnalysisAgentRunner(
            DataAnalysisTaskService analysisTaskService,
            AgentRunStepService stepService,
            ModelCallMapper modelCallMapper,
            AgentVersionMapper agentVersionMapper,
            SkillVersionMapper skillVersionMapper,
            SkillDefinitionMapper skillDefinitionMapper,
            SkillVersionToolMapper skillVersionToolMapper,
            ToolVersionMapper toolVersionMapper,
            ModelGateway modelGateway,
            ToolRegistry toolRegistry,
            ToolExecutionService toolExecutionService,
            RunEventService eventService,
            SseHub sseHub,
            ObjectMapper objectMapper,
            ToolRuntimeProperties toolProperties,
            AgentRuntimeProperties runtimeProperties) {
        this.analysisTaskService = analysisTaskService;
        this.stepService = stepService;
        this.modelCallMapper = modelCallMapper;
        this.agentVersionMapper = agentVersionMapper;
        this.skillVersionMapper = skillVersionMapper;
        this.skillDefinitionMapper = skillDefinitionMapper;
        this.skillVersionToolMapper = skillVersionToolMapper;
        this.toolVersionMapper = toolVersionMapper;
        this.modelGateway = modelGateway;
        this.toolRegistry = toolRegistry;
        this.toolExecutionService = toolExecutionService;
        this.eventService = eventService;
        this.sseHub = sseHub;
        this.objectMapper = objectMapper;
        this.toolProperties = toolProperties;
        this.runtimeProperties = runtimeProperties;
    }

    public DataAnalysisRunResult execute(
            AgentRunRecord run,
            TaskRecord task,
            AiConnectionRuntimeConfig connection,
            String systemPrompt,
            Runnable controlCheck,
            Consumer<ModelCallRecord> activeCallConsumer) {
        DataAnalysisRunContext context =
                analysisTaskService.requireRunContext(task.getId());
        ToolScope toolScope = resolveToolScope(run, context.workspaceId());
        RunBudget budget = new RunBudget(toolProperties, runtimeProperties);
        List<AiConversationItem> conversation = new ArrayList<>();
        conversation.add(AiConversationItem.user(initialPrompt(context)));
        List<AiConversationItem> pendingResponsesInput =
                List.copyOf(conversation);
        String previousResponseId = null;
        int toolCallCount = 0;
        List<String> chartSpecs = new ArrayList<>();

        AgentRunStepRecord planStep = stepService.start(
                run.getId(),
                "PLAN",
                "准备数据分析上下文",
                "数据集 " + context.datasetId() + "，工作表 " + context.sheetId());
        stepService.complete(
                planStep.getId(),
                "限制为 " + maxSteps() + " 步和 "
                        + maxToolCalls() + " 次工具调用");

        for (int stepIndex = 1; stepIndex < maxSteps(); stepIndex++) {
            controlCheck.run();
            budget.checkConversation(conversation);
            AgentRunStepRecord modelStep = stepService.start(
                    run.getId(),
                    "MODEL",
                    "模型分析第 " + stepIndex + " 轮",
                    "上下文项 " + conversation.size());
            ModelCallRecord modelCall = createModelCall(
                    run, connection, previousResponseId);
            activeCallConsumer.accept(modelCall);
            stepService.attachModelCall(modelStep.getId(), modelCall.getId());

            ModelTurn turn;
            try {
                List<AiConversationItem> invocationConversation =
                        "RESPONSES".equals(connection.protocol())
                                && previousResponseId != null
                                ? pendingResponsesInput
                                : List.copyOf(conversation);
                turn = invoke(
                        run,
                        connection,
                        systemPrompt,
                        invocationConversation,
                        previousResponseId,
                        modelCall,
                        controlCheck,
                        budget,
                        toolScope);
            } catch (AgentRunControlException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                failModelCall(modelCall, exception);
                stepService.fail(
                        modelStep.getId(),
                        errorCode(exception),
                        safeSummary(exception));
                throw exception;
            }

            if (!turn.toolCalls().isEmpty()) {
                finishModelCall(modelCall, turn);
                activeCallConsumer.accept(null);
                stepService.complete(
                        modelStep.getId(),
                        "模型请求 " + turn.toolCalls().size() + " 次工具调用");
                conversation.add(AiConversationItem.assistant(
                        turn.text(), turn.toolCalls()));
                List<AiConversationItem> toolResults = new ArrayList<>();
                for (AiToolCall toolCall : turn.toolCalls()) {
                    controlCheck.run();
                    toolCallCount++;
                    if (toolCallCount > maxToolCalls()) {
                        throw new ApiException(
                                HttpStatus.CONFLICT,
                                ErrorCode.AGENT_STEP_LIMIT_EXCEEDED,
                                "Agent 工具调用次数超过限制");
                    }
                    AgentRunStepRecord toolStep = stepService.start(
                            run.getId(),
                            "TOOL",
                            "调用 " + toolCall.name(),
                            "ToolCall " + toolCall.id());
                    try {
                        ToolExecutionOutcome outcome = toolExecutionService.execute(
                                new ToolExecutionContext(
                                        run.getId(),
                                        toolStep.getId(),
                                        modelCall.getId(),
                                        context.workspaceId(),
                                        context.datasetId(),
                                        context.sheetId(),
                                        context.columnOverrides(),
                                        toolScope.versionIdsByCode(),
                                        toolScope.enforce(),
                                        controlCheck),
                                toolCall);
                        String resultJson = writeJson(outcome.result());
                        budget.addContext(resultJson);
                        AiConversationItem toolResult =
                                AiConversationItem.toolResult(
                                        toolCall.id(),
                                        toolCall.name(),
                                        resultJson);
                        conversation.add(toolResult);
                        toolResults.add(toolResult);
                        if ("dataset.chart_spec".equals(toolCall.name())
                                && context.expectedOutputs().contains("CHART_SPEC")) {
                            chartSpecs.add(resultJson);
                        }
                        stepService.complete(
                                toolStep.getId(),
                                outcome.toolCall().getResultSummary());
                    } catch (AgentRunControlException exception) {
                        throw exception;
                    } catch (RuntimeException exception) {
                        stepService.fail(
                                toolStep.getId(),
                                errorCode(exception),
                                safeSummary(exception));
                        throw exception;
                    }
                }
                pendingResponsesInput = List.copyOf(toolResults);
                if ("RESPONSES".equals(connection.protocol())) {
                    previousResponseId = turn.providerRequestId();
                }
                continue;
            }

            if (turn.text() == null || turn.text().isBlank()) {
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        ErrorCode.INVALID_STRUCTURED_OUTPUT,
                        "模型未返回分析报告或 ToolCall");
            }
            if (context.expectedOutputs().contains("CHART_SPEC")
                    && chartSpecs.isEmpty()) {
                ApiException exception = new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        ErrorCode.INVALID_STRUCTURED_OUTPUT,
                        "任务要求生成图表，但模型未返回图表规格");
                stepService.fail(
                        modelStep.getId(),
                        exception.getErrorCode(),
                        exception.getMessage());
                throw exception;
            }
            stepService.complete(
                    modelStep.getId(),
                    "模型已生成最终分析报告");
            AgentRunStepRecord artifactStep = stepService.start(
                    run.getId(),
                    "ARTIFACT",
                    "生成分析成果",
                    "报告与图表版本化");
            return new DataAnalysisRunResult(
                    context,
                    modelCall,
                    turn.text(),
                    List.copyOf(chartSpecs),
                    turn.providerRequestId(),
                    turn.usage(),
                    artifactStep.getId());
        }
        throw new ApiException(
                HttpStatus.CONFLICT,
                ErrorCode.AGENT_STEP_LIMIT_EXCEEDED,
                "Agent 步骤数量超过限制");
    }

    public void completeArtifactStep(DataAnalysisRunResult result) {
        stepService.complete(
                result.artifactStepId(),
                "已生成分析报告和 " + result.chartSpecs().size() + " 个图表成果");
    }

    public void failArtifactStep(DataAnalysisRunResult result,
                                 ErrorCode errorCode,
                                 String summary) {
        if (result != null && result.artifactStepId() != null) {
            stepService.fail(result.artifactStepId(), errorCode, summary);
        }
    }

    private ModelTurn invoke(AgentRunRecord run,
                             AiConnectionRuntimeConfig connection,
                             String systemPrompt,
                             List<AiConversationItem> conversation,
                             String previousResponseId,
                              ModelCallRecord modelCall,
                              Runnable controlCheck,
                              RunBudget budget,
                              ToolScope toolScope) {
        StringBuilder text = new StringBuilder();
        List<AiToolCall> toolCalls = new ArrayList<>();
        AtomicReference<String> providerRequestId = new AtomicReference<>();
        AtomicReference<AiUsage> usage = new AtomicReference<>();
        AtomicBoolean completed = new AtomicBoolean();
        TextDeltaBuffer deltaBuffer = TextDeltaBuffer.createDefault();
        modelGateway.stream(new AiInvocation(
                        connection,
                        systemPrompt,
                        "",
                        run.getId(),
                        modelCall.getId(),
                        true,
                        MAX_OUTPUT_TOKENS,
                        toolScope.enforce()
                                ? toolRegistry.modelDefinitions(toolScope.versionIdsByCode())
                                : toolRegistry.modelDefinitions(),
                        conversation,
                        previousResponseId))
                .doOnNext(event -> {
                    controlCheck.run();
                    budget.accept(event);
                    if (event.providerRequestId() != null) {
                        providerRequestId.set(event.providerRequestId());
                    }
                    if (event.usage() != null) {
                        usage.set(event.usage());
                    }
                    if ("run.completed".equals(event.type()) && event.done()) {
                        flushDelta(
                                run.getId(),
                                modelCall.getId(),
                                deltaBuffer,
                                providerRequestId.get());
                        completed.set(true);
                        return;
                    }
                    if ("output.text.delta".equals(event.type())) {
                        if (event.text() != null) {
                            text.append(event.text());
                        }
                        if (deltaBuffer.append(event.text())) {
                            flushDelta(
                                    run.getId(),
                                    modelCall.getId(),
                                    deltaBuffer,
                                    providerRequestId.get());
                        }
                        return;
                    }
                    if (event.toolCall() != null) {
                        toolCalls.add(event.toolCall());
                        return;
                    }
                    if ("output.text.completed".equals(event.type())
                            || "usage.updated".equals(event.type())) {
                        flushDelta(
                                run.getId(),
                                modelCall.getId(),
                                deltaBuffer,
                                providerRequestId.get());
                        persistModelEvent(
                                run.getId(), modelCall.getId(), event);
                    }
                })
                .blockLast(budget.remaining());
        flushDelta(
                run.getId(),
                modelCall.getId(),
                deltaBuffer,
                providerRequestId.get());
        if (!completed.get()) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    ErrorCode.STREAM_INTERRUPTED,
                    "模型流未正常完成");
        }
        return new ModelTurn(
                text.toString(),
                List.copyOf(toolCalls),
                providerRequestId.get(),
                usage.get());
    }

    private ToolScope resolveToolScope(AgentRunRecord run, Long workspaceId) {
        if (run.getAgentVersionId() == null) {
            return new ToolScope(Map.of(), false);
        }
        AgentVersionRecord agentVersion = agentVersionMapper.findById(run.getAgentVersionId());
        if (agentVersion == null || !"PUBLISHED".equals(agentVersion.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.AGENT_VERSION_INVALID,
                    "运行绑定的 AgentVersion 不存在或未发布");
        }
        Map<String, Object> configuration = readConfiguration(agentVersion.getConfigurationJson());
        boolean enforce = hasVersionIds(configuration.get("toolVersionIds"))
                || hasVersionIds(configuration.get("skillVersionIds"));
        if (!enforce) {
            return new ToolScope(Map.of(), false);
        }

        Map<String, Long> toolIdsByCode = new LinkedHashMap<>();
        for (Long toolVersionId : readVersionIds(
                configuration.get("toolVersionIds"), "configuration.toolVersionIds")) {
            addToolVersion(toolVersionId, toolIdsByCode);
        }
        for (Long skillVersionId : readVersionIds(
                configuration.get("skillVersionIds"), "configuration.skillVersionIds")) {
            SkillVersionRecord skillVersion = skillVersionMapper.findById(skillVersionId);
            if (skillVersion == null || !"PUBLISHED".equals(skillVersion.getStatus())) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        ErrorCode.SKILL_VERSION_INVALID,
                        "运行绑定的 SkillVersion 不存在或未发布");
            }
            SkillDefinitionRecord skill = skillDefinitionMapper
                    .findById(skillVersion.getSkillDefinitionId());
            if (skill == null
                    || !workspaceId.equals(skill.getWorkspaceId())
                    || !"PUBLISHED".equals(skill.getStatus())) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        ErrorCode.SKILL_VERSION_INVALID,
                        "运行绑定的 SkillVersion 不属于当前 Workspace 或已停用");
            }
            for (Long toolVersionId : skillVersionToolMapper
                    .findToolVersionIds(skillVersionId)) {
                addToolVersion(toolVersionId, toolIdsByCode);
            }
        }
        return new ToolScope(Map.copyOf(toolIdsByCode), true);
    }

    private boolean hasVersionIds(Object value) {
        return value instanceof List<?> values && !values.isEmpty();
    }

    private void addToolVersion(Long toolVersionId,
                                Map<String, Long> toolIdsByCode) {
        ToolVersionRecord toolVersion = toolVersionMapper.findById(toolVersionId);
        if (toolVersion == null
                || !"PUBLISHED".equals(toolVersion.getStatus())
                || !"PUBLISHED".equals(toolVersion.getCatalogStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.TOOL_DISABLED,
                    "运行绑定的 ToolVersion 不存在、未发布或已停用");
        }
        Long existing = toolIdsByCode.putIfAbsent(
                toolVersion.getToolCode(), toolVersion.getId());
        if (existing != null && !existing.equals(toolVersion.getId())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.AGENT_VERSION_INVALID,
                    "同一个 toolCode 不能绑定多个不同 ToolVersion: "
                            + toolVersion.getToolCode());
        }
    }

    private Map<String, Object> readConfiguration(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value,
                    objectMapper.getTypeFactory().constructMapType(
                            LinkedHashMap.class, String.class, Object.class));
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.AGENT_VERSION_INVALID,
                    "运行绑定的 AgentVersion 配置不是有效 JSON");
        }
    }

    private List<Long> readVersionIds(Object value, String field) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> values)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.AGENT_VERSION_INVALID,
                    field + " 必须是正整数数组");
        }
        List<Long> ids = new ArrayList<>();
        Set<Long> unique = new LinkedHashSet<>();
        for (Object item : values) {
            Long id = positiveId(item);
            if (id == null || !unique.add(id)) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        ErrorCode.AGENT_VERSION_INVALID,
                        field + " 必须是无重复的正整数数组");
            }
            ids.add(id);
        }
        return ids;
    }

    private Long positiveId(Object value) {
        if (value instanceof Number number) {
            long id = number.longValue();
            return id > 0 && number.doubleValue() == id ? id : null;
        }
        if (value instanceof String text) {
            try {
                long id = Long.parseLong(text.trim());
                return id > 0 ? id : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private record ToolScope(Map<String, Long> versionIdsByCode,
                             boolean enforce) {
    }

    private ModelCallRecord createModelCall(
            AgentRunRecord run,
            AiConnectionRuntimeConfig connection,
            String previousResponseId) {
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
        call.setPreviousResponseId(previousResponseId);
        call.setRetryCount(0);
        call.setCreatedAt(Instant.now());
        modelCallMapper.insert(call);
        return call;
    }

    private void finishModelCall(ModelCallRecord call, ModelTurn turn) {
        modelCallMapper.updateFinished(
                call.getId(),
                "SUCCEEDED",
                Instant.now(),
                turn.providerRequestId(),
                turn.usage() == null ? null : turn.usage().inputTokens(),
                turn.usage() == null ? null : turn.usage().outputTokens(),
                null,
                null);
    }

    private void failModelCall(ModelCallRecord call, RuntimeException exception) {
        ErrorCode errorCode = errorCode(exception);
        modelCallMapper.updateFinished(
                call.getId(),
                "FAILED",
                Instant.now(),
                null,
                null,
                null,
                errorCode.name(),
                safeSummary(exception));
    }

    private void persistModelEvent(Long runId,
                                   Long modelCallId,
                                   AiStreamEvent event) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("text", event.text());
        data.put("done", event.done());
        data.put("providerRequestId", event.providerRequestId());
        if (event.usage() != null) {
            data.put("inputTokens", event.usage().inputTokens());
            data.put("outputTokens", event.usage().outputTokens());
        }
        RunEventRecord persisted = eventService.append(
                runId,
                modelCallId,
                event.type(),
                event.errorSummary(),
                data);
        sseHub.publish(persisted);
    }

    private void flushDelta(Long runId,
                            Long modelCallId,
                            TextDeltaBuffer buffer,
                            String providerRequestId) {
        if (!buffer.hasContent()) {
            return;
        }
        persistModelEvent(
                runId,
                modelCallId,
                new AiStreamEvent(
                        "output.text.delta",
                        buffer.drain(),
                        null,
                        providerRequestId,
                        false));
    }

    private String initialPrompt(DataAnalysisRunContext context) {
        return """
                任务标题：%s
                分析目标：%s
                数据集：%s（ID=%d，格式=%s）
                工作表：%s（ID=%d）
                规模：%d 行，%d 列
                期望成果：%s

                请先通过提供的只读工具获取结构、质量和必要聚合结果。
                不要猜测原始数据，不要请求 SQL、脚本或文件路径。
                完成分析后直接输出 Markdown 报告；需要图表时先调用 dataset.chart_spec。
                """.formatted(
                context.taskTitle(),
                context.analysisGoal(),
                context.datasetName(),
                context.datasetId(),
                context.datasetFormat(),
                context.sheetName(),
                context.sheetId(),
                context.rowCount(),
                context.columnCount(),
                context.expectedOutputs());
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    ErrorCode.TOOL_EXECUTION_FAILED,
                    "工具结果无法加入 Agent 上下文");
        }
    }

    private int maxSteps() {
        return Math.max(2, toolProperties.getMaxSteps());
    }

    private int maxToolCalls() {
        return Math.max(1, toolProperties.getMaxToolCalls());
    }

    private ErrorCode errorCode(Throwable exception) {
        if (exception instanceof AiProviderException providerException) {
            return providerException.getErrorCode();
        }
        if (exception instanceof ApiException apiException) {
            return apiException.getErrorCode();
        }
        if (exception instanceof TimeoutException) {
            return ErrorCode.REQUEST_TIMEOUT;
        }
        return ErrorCode.UNKNOWN_PROVIDER_ERROR;
    }

    private String safeSummary(Throwable exception) {
        String value = exception.getMessage();
        String normalized = value == null || value.isBlank()
                ? "数据分析运行失败"
                : value.replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .trim();
        return normalized.length() <= 512
                ? normalized : normalized.substring(0, 512);
    }

    private record ModelTurn(
            String text,
            List<AiToolCall> toolCalls,
            String providerRequestId,
            AiUsage usage
    ) {
    }

    private static final class RunBudget {
        private final long maxContextBytes;
        private final long maxOutputCharacters;
        private final long maxOutputBytes;
        private final long maxEvents;
        private final long deadlineNanos;
        private long contextBytes;
        private long outputCharacters;
        private long outputBytes;
        private long events;

        private RunBudget(ToolRuntimeProperties toolProperties,
                          AgentRuntimeProperties runtimeProperties) {
            this.maxContextBytes = Math.max(
                    1, toolProperties.getMaxContextBytes());
            this.maxOutputCharacters = Math.max(
                    1, runtimeProperties.getMaxOutputCharacters());
            this.maxOutputBytes = Math.max(
                    1, runtimeProperties.getMaxOutputBytes());
            this.maxEvents = Math.max(
                    1, runtimeProperties.getMaxStreamEvents());
            Duration duration = runtimeProperties.getMaxRunDuration();
            Duration normalized = duration == null
                    || duration.isZero()
                    || duration.isNegative()
                    ? Duration.ofMinutes(5)
                    : duration;
            this.deadlineNanos = System.nanoTime() + normalized.toNanos();
        }

        private void checkConversation(List<AiConversationItem> conversation) {
            long bytes = 0;
            for (AiConversationItem item : conversation) {
                bytes += utf8Length(item.content());
                if (item.toolCalls() != null) {
                    for (AiToolCall call : item.toolCalls()) {
                        bytes += utf8Length(call.name());
                        bytes += utf8Length(call.argumentsJson());
                    }
                }
            }
            contextBytes = bytes;
            checkContext();
        }

        private void addContext(String value) {
            contextBytes += utf8Length(value);
            checkContext();
        }

        private void accept(AiStreamEvent event) {
            events++;
            if (events > maxEvents) {
                throw limit(
                        ErrorCode.OUTPUT_LIMIT_EXCEEDED,
                        "模型流事件数量超过限制");
            }
            if (event.text() != null) {
                outputCharacters += event.text().length();
                outputBytes += utf8Length(event.text());
                if (outputCharacters > maxOutputCharacters
                        || outputBytes > maxOutputBytes) {
                    throw limit(
                            ErrorCode.OUTPUT_LIMIT_EXCEEDED,
                            "模型输出超过限制");
                }
            }
            if (System.nanoTime() >= deadlineNanos) {
                throw limit(
                        ErrorCode.REQUEST_TIMEOUT,
                        "Agent 运行超过时间限制");
            }
        }

        private Duration remaining() {
            long nanos = deadlineNanos - System.nanoTime();
            if (nanos <= 0) {
                throw limit(
                        ErrorCode.REQUEST_TIMEOUT,
                        "Agent 运行超过时间限制");
            }
            return Duration.ofNanos(nanos);
        }

        private void checkContext() {
            if (contextBytes > maxContextBytes) {
                throw limit(
                        ErrorCode.AGENT_CONTEXT_LIMIT_EXCEEDED,
                        "Agent 上下文超过限制");
            }
        }

        private static int utf8Length(String value) {
            return value == null
                    ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
        }

        private static ApiException limit(ErrorCode code, String message) {
            return new ApiException(HttpStatus.CONFLICT, code, message);
        }
    }
}
