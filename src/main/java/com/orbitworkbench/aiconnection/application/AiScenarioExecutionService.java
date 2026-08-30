package com.orbitworkbench.aiconnection.application;

import com.orbitworkbench.ai.application.AiCallFailures;
import com.orbitworkbench.ai.application.AiInvocation;
import com.orbitworkbench.ai.application.AiStreamEvent;
import com.orbitworkbench.ai.application.ModelGateway;
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

    public String executeText(AiScenario scenario, Long userId, Long pinnedConnectionId,
                              String systemPrompt, String userPrompt,
                              int maxOutputTokens, Duration timeout) {
        ResolvedRoute route = router.resolve(userId, scenario, pinnedConnectionId);
        int requestChars = chars(systemPrompt) + chars(userPrompt);
        Long auditId = recorder.start(userId, scenario, route, requestChars,
                recorder.snapshotJson(scenario, route, maxOutputTokens, true));
        long start = System.nanoTime();

        Attempt first = attempt(scenario, route.primary(), systemPrompt, userPrompt,
                maxOutputTokens, timeout, start);
        if (first.failure() == null) {
            recorder.finish(auditId, userId, scenario, AiCallAuditRecorder.STATUS_SUCCEEDED, null,
                    first.latencyMs(), requestChars, first.text().length(),
                    route.primary().connectionId(), false);
            return first.text();
        }

        if (route.canFailover() && FAILOVER_ELIGIBLE.contains(first.errorCode())) {
            log.warn("场景 {} 主用账户 {} 失败（{}），切换到备用账户 {} 重试",
                    scenario, route.primary().connectionId(), first.errorCode(),
                    route.backup().connectionId());
            Attempt second = attempt(scenario, route.backup(), systemPrompt, userPrompt,
                    maxOutputTokens, timeout, start);
            if (second.failure() == null) {
                recorder.finish(auditId, userId, scenario, AiCallAuditRecorder.STATUS_SUCCEEDED, null,
                        second.latencyMs(), requestChars, second.text().length(),
                        route.backup().connectionId(), true);
                return second.text();
            }
            recorder.finish(auditId, userId, scenario, AiCallAuditRecorder.STATUS_FAILED,
                    second.errorCode().name(), second.latencyMs(), requestChars,
                    second.partialChars(), route.backup().connectionId(), true);
            throw second.failure();
        }

        recorder.finish(auditId, userId, scenario, AiCallAuditRecorder.STATUS_FAILED,
                first.errorCode().name(), first.latencyMs(), requestChars, first.partialChars(),
                route.primary().connectionId(), false);
        throw first.failure();
    }

    private Attempt attempt(AiScenario scenario, AiConnectionRuntimeConfig connection,
                            String systemPrompt, String userPrompt, int maxOutputTokens,
                            Duration timeout, long start) {
        StringBuilder text = new StringBuilder();
        try {
            modelGateway.stream(new AiInvocation(connection, systemPrompt, userPrompt,
                            null, null, true, maxOutputTokens))
                    .doOnNext(event -> {
                        if (event.text() != null) {
                            text.append(event.text());
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
        return Attempt.succeeded(text.toString(), elapsedMillis(start));
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
                           ErrorCode errorCode, ApiException failure) {

        static Attempt succeeded(String text, int latencyMs) {
            return new Attempt(text, text.length(), latencyMs, null, null);
        }

        static Attempt failed(ErrorCode errorCode, ApiException failure, int partialChars, int latencyMs) {
            return new Attempt(null, partialChars, latencyMs, errorCode, failure);
        }
    }
}
