package com.orbitworkbench.aiconnection.application;

import com.orbitworkbench.ai.application.AiCallFailures;
import com.orbitworkbench.ai.application.AiInvocation;
import com.orbitworkbench.ai.application.AiStreamEvent;
import com.orbitworkbench.ai.application.ModelGateway;
import com.orbitworkbench.ai.application.WebSearchDecision;
import com.orbitworkbench.ai.application.WebSearchMode;
import com.orbitworkbench.aiconnection.application.AiConnectionService.AiConnectionRuntimeConfig;
import com.orbitworkbench.aiconnection.application.AiScenarioRouter.ResolvedRoute;
import com.orbitworkbench.aiconnection.domain.AiScenario;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import java.time.Duration;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 业务场景统一的阻塞式文本生成入口：解析场景账户、记录调用审计、按白名单做备用切换，
 * 并把上游错误按其原始错误码透传为业务异常。
 *
 * <p>故障切换只对可重试的上游类失败生效；凭据错误、模型不存在、请求非法等"换账户也一样失败"
 * 或"换账户可能掩盖配置问题"的错误一律直接返回，不做静默切换。
 */
@Service
public class AiScenarioExecutionService {

    /** 允许切换到备用账户的错误码白名单，其余一律按原样失败返回。 */
    private static final Set<ErrorCode> FAILOVER_ELIGIBLE = Set.of(
            ErrorCode.UPSTREAM_UNAVAILABLE,
            ErrorCode.REQUEST_TIMEOUT,
            ErrorCode.STREAM_INTERRUPTED,
            ErrorCode.RATE_LIMITED,
            ErrorCode.UNKNOWN_PROVIDER_ERROR);

    private static final Logger log = LoggerFactory.getLogger(AiScenarioExecutionService.class);

    private final AiScenarioRouter router;
    private final AiCallAuditRecorder recorder;
    private final ModelGateway modelGateway;

    public AiScenarioExecutionService(AiScenarioRouter router,
                                      AiCallAuditRecorder recorder,
                                      ModelGateway modelGateway) {
        this.router = router;
        this.recorder = recorder;
        this.modelGateway = modelGateway;
    }

    /**
     * 开启一次流式生成会话（SSE 化共用底座）。调用方 {@code begin()} 后自行消费
     * {@code deltas()}，收尾时调 {@code succeed}/{@code fail}。流式路径不做备用切换。
     */
    public ScenarioStreamSession stream(AiScenario scenario, Long userId, Long pinnedConnectionId,
                                        String systemPrompt, String userPrompt,
                                        int maxOutputTokens, Duration timeout) {
        return new ScenarioStreamSession(router, recorder, modelGateway, scenario, userId,
                pinnedConnectionId, systemPrompt, userPrompt, maxOutputTokens, timeout, WebSearchMode.DISABLED);
    }

    public ScenarioStreamSession stream(AiScenario scenario, Long userId, Long pinnedConnectionId,
                                        String systemPrompt, String userPrompt,
                                        int maxOutputTokens, Duration timeout, WebSearchMode webSearch) {
        return new ScenarioStreamSession(router, recorder, modelGateway, scenario, userId,
                pinnedConnectionId, systemPrompt, userPrompt, maxOutputTokens, timeout, webSearch);
    }

    public String executeText(AiScenario scenario, Long userId, Long pinnedConnectionId,
                              String systemPrompt, String userPrompt,
                              int maxOutputTokens, Duration timeout) {
        return executeText(scenario, userId, pinnedConnectionId, systemPrompt, userPrompt,
                maxOutputTokens, timeout, WebSearchMode.DISABLED);
    }

    /**
     * @param requestedWebSearch 业务侧的联网意愿。结论在主用账户上一次算清并写进审计快照，
     *     「必须联网」在这一步就直接拒绝，不会先生成一半再失败。
     */
    public String executeText(AiScenario scenario, Long userId, Long pinnedConnectionId,
                              String systemPrompt, String userPrompt,
                              int maxOutputTokens, Duration timeout, WebSearchMode requestedWebSearch) {
        ResolvedRoute route = router.resolve(userId, scenario, pinnedConnectionId);
        WebSearchDecision webSearch = WebSearchDecision.resolve(requestedWebSearch, route.primary());
        int requestChars = chars(systemPrompt) + chars(userPrompt);
        Long auditId = recorder.start(userId, scenario, route, requestChars,
                recorder.snapshotJson(scenario, route, maxOutputTokens, true, webSearch));
        long start = System.nanoTime();

        Attempt first = attempt(scenario, route.primary(), systemPrompt, userPrompt,
                maxOutputTokens, timeout, start, webSearch.effective());
        if (first.failure() == null) {
            recorder.finish(auditId, userId, scenario, AiCallAuditRecorder.STATUS_SUCCEEDED, null,
                    first.latencyMs(), requestChars, first.text().length(),
                    first.inputTokens(), first.outputTokens(),
                    AiCallAuditRecorder.cost(route.primary(), first.inputTokens(), first.outputTokens()),
                    route.primary().connectionId(), false);
            return first.text();
        }

        if (route.canFailover() && FAILOVER_ELIGIBLE.contains(first.errorCode())) {
            log.warn("场景 {} 主用账户 {} 失败（{}），切换到备用账户 {} 重试",
                    scenario, route.primary().connectionId(), first.errorCode(),
                    route.backup().connectionId());
            // 备用账户的联网形状可能与主用不同，按它自己的声明重算，不能沿用主用的结论。
            Attempt second = attempt(scenario, route.backup(), systemPrompt, userPrompt,
                    maxOutputTokens, timeout, start,
                    WebSearchDecision.resolve(requestedWebSearch, route.backup()).effective());
            if (second.failure() == null) {
                recorder.finish(auditId, userId, scenario, AiCallAuditRecorder.STATUS_SUCCEEDED, null,
                        second.latencyMs(), requestChars, second.text().length(),
                        second.inputTokens(), second.outputTokens(),
                        AiCallAuditRecorder.cost(route.backup(), second.inputTokens(), second.outputTokens()),
                        route.backup().connectionId(), true);
                return second.text();
            }
            recorder.finish(auditId, userId, scenario, AiCallAuditRecorder.STATUS_FAILED,
                    second.errorCode().name(), second.latencyMs(), requestChars,
                    second.partialChars(), second.inputTokens(), second.outputTokens(),
                    AiCallAuditRecorder.cost(route.backup(), second.inputTokens(), second.outputTokens()),
                    route.backup().connectionId(), true);
            throw second.failure();
        }

        recorder.finish(auditId, userId, scenario, AiCallAuditRecorder.STATUS_FAILED,
                first.errorCode().name(), first.latencyMs(), requestChars, first.partialChars(),
                first.inputTokens(), first.outputTokens(),
                AiCallAuditRecorder.cost(route.primary(), first.inputTokens(), first.outputTokens()),
                route.primary().connectionId(), false);
        throw first.failure();
    }

    private Attempt attempt(AiScenario scenario, AiConnectionRuntimeConfig connection,
                            String systemPrompt, String userPrompt, int maxOutputTokens,
                            Duration timeout, long start, WebSearchMode webSearch) {
        StringBuilder text = new StringBuilder();
        // usage 由适配器在流尾以 usage.updated / run.completed 事件上报；留最后一个（累计值最全）。
        java.util.concurrent.atomic.AtomicReference<com.orbitworkbench.ai.application.AiUsage> usage =
                new java.util.concurrent.atomic.AtomicReference<>();
        try {
            modelGateway.stream(new AiInvocation(connection, systemPrompt, userPrompt,
                            null, null, true, maxOutputTokens).withWebSearch(webSearch))
                    .doOnNext(event -> {
                        if (event.text() != null) {
                            text.append(event.text());
                        }
                        if (event.usage() != null) {
                            usage.set(event.usage());
                        }
                    })
                    .blockLast(timeout);
        } catch (RuntimeException exception) {
            ApiException mapped = toApiException(scenario, exception);
            return Attempt.failed(mapped.getErrorCode(), mapped, text.length(), elapsedMillis(start));
        }
        if (text.isEmpty()) {
            ApiException blank = new ApiException(HttpStatus.BAD_GATEWAY,
                    ErrorCode.INVALID_STRUCTURED_OUTPUT, scenario.label() + "未返回任何内容");
            return Attempt.failed(blank.getErrorCode(), blank, 0, elapsedMillis(start));
        }
        com.orbitworkbench.ai.application.AiUsage observed = usage.get();
        return Attempt.succeeded(text.toString(), elapsedMillis(start),
                observed == null ? null : observed.inputTokens(),
                observed == null ? null : observed.outputTokens());
    }

    /**
     * 错误码透传：适配器已经区分好的上游语义不再被统一压成 502 UPSTREAM_UNAVAILABLE。
     */
    private ApiException toApiException(AiScenario scenario, RuntimeException exception) {
        return AiCallFailures.toApiException(scenario.label(), exception);
    }

    private int elapsedMillis(long start) {
        return Math.max(0, (int) ((System.nanoTime() - start) / 1_000_000L));
    }

    private int chars(String value) {
        return value == null ? 0 : value.length();
    }

    private record Attempt(String text, int partialChars, int latencyMs,
                           ErrorCode errorCode, ApiException failure,
                           Integer inputTokens, Integer outputTokens) {

        static Attempt succeeded(String text, int latencyMs, Integer inputTokens, Integer outputTokens) {
            return new Attempt(text, text.length(), latencyMs, null, null, inputTokens, outputTokens);
        }

        static Attempt failed(ErrorCode errorCode, ApiException failure, int partialChars, int latencyMs) {
            return new Attempt(null, partialChars, latencyMs, errorCode, failure, null, null);
        }
    }
}
