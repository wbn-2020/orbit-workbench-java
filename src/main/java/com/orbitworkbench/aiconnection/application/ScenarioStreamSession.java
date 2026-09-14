package com.orbitworkbench.aiconnection.application;

import com.orbitworkbench.ai.application.AiCallFailures;
import com.orbitworkbench.ai.application.AiInvocation;
import com.orbitworkbench.ai.application.AiStreamEvent;
import com.orbitworkbench.ai.application.ModelGateway;
import com.orbitworkbench.ai.application.WebSearchDecision;
import com.orbitworkbench.ai.application.WebSearchMode;
import com.orbitworkbench.aiconnection.domain.AiScenario;
import com.orbitworkbench.shared.api.ApiException;
import java.time.Duration;
import reactor.core.publisher.Flux;

/**
 * 业务场景统一的流式文本生成入口（SSE 化共用底座）。
 *
 * <p>与 {@link AiScenarioExecutionService#executeText} 同一套账户解析与审计口径：
 * 流式路径不做备用切换（与面试出题流式的既定决策一致——流已开始后切换会造成内容拼接错乱），
 * 失败通过 {@code onErrorResume} 交给调用方下发 error 事件并正常收流。
 */
public final class ScenarioStreamSession {

    private final AiScenarioRouter router;
    private final AiCallAuditRecorder recorder;
    private final ModelGateway modelGateway;
    private final AiScenario scenario;
    private final Long userId;
    private final Long pinnedConnectionId;
    private final String systemPrompt;
    private final String userPrompt;
    private final int maxOutputTokens;
    private final Duration timeout;
    private final WebSearchMode requestedWebSearch;
    /** V45 注入溯源：本次注入的个人记忆上下文（可空），进配置快照。 */
    private final com.orbitworkbench.ai.application.MemoryContext memory;
    private WebSearchDecision webSearch;
    private final java.util.concurrent.atomic.AtomicBoolean finished = new java.util.concurrent.atomic.AtomicBoolean();

    private Long auditId;
    private int requestChars;
    private long start;
    private AiScenarioRouter.ResolvedRoute resolvedRoute;
    /** 流尾 usage（适配器在 usage.updated / run.completed 上报）；null 表示上游没报，不是 0。 */
    private final java.util.concurrent.atomic.AtomicReference<com.orbitworkbench.ai.application.AiUsage> observedUsage =
            new java.util.concurrent.atomic.AtomicReference<>();

    ScenarioStreamSession(AiScenarioRouter router, AiCallAuditRecorder recorder,
                          ModelGateway modelGateway, AiScenario scenario, Long userId,
                          Long pinnedConnectionId, String systemPrompt, String userPrompt,
                          int maxOutputTokens, Duration timeout, WebSearchMode requestedWebSearch) {
        this(router, recorder, modelGateway, scenario, userId, pinnedConnectionId, systemPrompt,
                userPrompt, maxOutputTokens, timeout, requestedWebSearch, null);
    }

    ScenarioStreamSession(AiScenarioRouter router, AiCallAuditRecorder recorder,
                          ModelGateway modelGateway, AiScenario scenario, Long userId,
                          Long pinnedConnectionId, String systemPrompt, String userPrompt,
                          int maxOutputTokens, Duration timeout, WebSearchMode requestedWebSearch,
                          com.orbitworkbench.ai.application.MemoryContext memory) {
        this.router = router;
        this.recorder = recorder;
        this.modelGateway = modelGateway;
        this.scenario = scenario;
        this.userId = userId;
        this.pinnedConnectionId = pinnedConnectionId;
        this.systemPrompt = systemPrompt;
        this.userPrompt = userPrompt;
        this.maxOutputTokens = maxOutputTokens;
        this.timeout = timeout;
        this.requestedWebSearch = requestedWebSearch;
        this.memory = memory;
    }

    /** 开流前调用：解析账户并写 RUNNING 审计。抛出的 ApiException 语义是「流未开始」，调用方可直接回 4xx/5xx。 */
    public void begin() {
        resolvedRoute = router.resolve(userId, scenario, pinnedConnectionId);
        webSearch = WebSearchDecision.resolve(requestedWebSearch, resolvedRoute.primary());
        requestChars = chars(systemPrompt) + chars(userPrompt);
        start = System.nanoTime();
        auditId = recorder.start(userId, scenario, resolvedRoute, requestChars,
                recorder.snapshotJson(scenario, resolvedRoute, maxOutputTokens, true,
                        webSearch, memory));
    }

    /** 增量事件流。文本事件原样透传；上游失败映射为 ApiException 供调用方发 error 事件。 */
    public Flux<String> deltas() {
        return Flux.defer(() -> modelGateway.stream(new AiInvocation(
                        resolvedRoute.primary(), systemPrompt, userPrompt,
                        null, null, true, maxOutputTokens).withWebSearch(webSearch.effective())))
                .takeUntilOther(reactor.core.publisher.Mono.delay(timeout)
                        .flatMap(ignored -> reactor.core.publisher.Mono.error(
                                new java.util.concurrent.TimeoutException("Model generation timed out"))))
                .doOnNext(event -> {
                    if (event.usage() != null) {
                        observedUsage.set(event.usage());
                    }
                })
                .mapNotNull(AiStreamEvent::text);
    }

    /** 正常收流：审计 SUCCEEDED。fullText 由调用方按已发增量统计。 */
    public void succeed(int fullTextChars) {
        if (!finished.compareAndSet(false, true)) return;
        com.orbitworkbench.ai.application.AiUsage observed = observedUsage.get();
        recorder.finish(auditId, userId, scenario, AiCallAuditRecorder.STATUS_SUCCEEDED, null,
                elapsedMillis(), requestChars, fullTextChars, observed,
                AiCallAuditRecorder.cost(resolvedRoute.primary(), observed),
                resolvedRoute.primary().connectionId(), false);
    }

    /** 失败收流：把任意异常映射为带真实错误码的 ApiException 并记 FAILED 审计。 */
    public ApiException fail(RuntimeException failure, int partialChars) {
        ApiException mapped = AiCallFailures.toApiException(scenario.label(), failure);
        if (!finished.compareAndSet(false, true)) return mapped;
        // 失败也要记账：上游可能已经处理了部分 token（usage 已上报），照实记录；
        // 没上报就是 null，不编一个数出来。
        com.orbitworkbench.ai.application.AiUsage observed = observedUsage.get();
        recorder.finish(auditId, userId, scenario, AiCallAuditRecorder.STATUS_FAILED,
                mapped.getErrorCode().name(), elapsedMillis(), requestChars, partialChars, observed,
                AiCallAuditRecorder.cost(resolvedRoute.primary(), observed),
                resolvedRoute.primary().connectionId(), false);
        return mapped;
    }

    private int elapsedMillis() {
        return (int) ((System.nanoTime() - start) / 1_000_000);
    }

    private static int chars(String s) {
        return s == null ? 0 : s.length();
    }
}
