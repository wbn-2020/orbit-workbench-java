package com.orbitworkbench.aiconnection.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.ai.application.WebSearchDecision;
import com.orbitworkbench.aiconnection.application.AiConnectionService.AiConnectionRuntimeConfig;
import com.orbitworkbench.aiconnection.domain.AiScenario;
import com.orbitworkbench.aiconnection.domain.CallAuditRecord;
import com.orbitworkbench.aiconnection.infrastructure.mapper.AiScenarioMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 调用审计写入。审计行必须独立于业务事务提交，否则业务失败回滚会把最需要留痕的失败记录一起带走。
 *
 * <p>只记录长度与配置摘要，不记录请求正文、模型原文和任何凭据。写入失败仅告警，不影响业务链路。
 */
@Service
public class AiCallAuditRecorder {

    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_FAILED = "FAILED";

    private static final int MAX_ERROR_CODE_LENGTH = 64;
    private static final int MAX_SNAPSHOT_CHARS = 2000;

    private static final Logger log = LoggerFactory.getLogger(AiCallAuditRecorder.class);

    private final AiScenarioMapper mapper;
    private final ObjectMapper objectMapper;

    public AiCallAuditRecorder(AiScenarioMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long start(Long userId, AiScenario scenario, AiScenarioRouter.ResolvedRoute route,
                      int requestChars, String configurationSnapshotJson) {
        try {
            CallAuditRecord record = new CallAuditRecord();
            record.setUserId(userId);
            record.setScenarioCode(scenario.name());
            record.setPrimaryConnectionId(route.primary().connectionId());
            record.setUsedConnectionId(route.primary().connectionId());
            record.setBackupAttempted(false);
            record.setStatus(STATUS_RUNNING);
            record.setRequestChars(Math.max(0, requestChars));
            record.setResponseChars(0);
            record.setConfigurationSnapshotJson(snapshot(configurationSnapshotJson));
            record.setCreatedAt(Instant.now());
            mapper.insertAudit(record);
            return record.getId();
        } catch (RuntimeException exception) {
            log.warn("写入 AI 调用审计失败（开始），scenario={} userId={}", scenario, userId, exception);
            return null;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finish(Long auditId, Long userId, AiScenario scenario, String status, String errorCode,
                       int latencyMs, int requestChars, int responseChars, Long usedConnectionId,
                       boolean backupAttempted) {
        finish(auditId, userId, scenario, status, errorCode, latencyMs, requestChars, responseChars,
                null, null, usedConnectionId, backupAttempted);
    }

    /**
     * 带用量与成本的收尾。usage 允许 null——供应商没报 token 就说不知道，
     * 不用 0 冒充「这次没花钱」（同 audit 表列可空的设计口径）。
     * V42：明细（cached/reasoning）随 usage 一起落库，缓存是输入的子集、推理是输出的子集。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finish(Long auditId, Long userId, AiScenario scenario, String status, String errorCode,
                       int latencyMs, int requestChars, int responseChars,
                       com.orbitworkbench.ai.application.AiUsage usage,
                       java.math.BigDecimal costAmount,
                       Long usedConnectionId, boolean backupAttempted) {
        if (auditId == null) {
            return;
        }
        try {
            mapper.finishAudit(auditId, userId, status, truncate(errorCode, MAX_ERROR_CODE_LENGTH),
                    Math.max(0, latencyMs), Math.max(0, requestChars), Math.max(0, responseChars),
                    usage == null || usage.inputTokens() == null ? null : Math.max(0, usage.inputTokens()),
                    usage == null || usage.outputTokens() == null ? null : Math.max(0, usage.outputTokens()),
                    usage == null || usage.cachedInputTokens() == null
                            ? null : Math.max(0, usage.cachedInputTokens()),
                    usage == null || usage.reasoningOutputTokens() == null
                            ? null : Math.max(0, usage.reasoningOutputTokens()),
                    costAmount, usedConnectionId, backupAttempted, Instant.now());
        } catch (RuntimeException exception) {
            log.warn("写入 AI 调用审计失败（结束），scenario={} userId={} auditId={}",
                    scenario, userId, auditId, exception);
        }
    }

    /** 配置快照：只含非敏感的路由与模型参数，供事后解释"这次到底走了哪个账户"。 */
    public String snapshotJson(AiScenario scenario, AiScenarioRouter.ResolvedRoute route,
                               int maxOutputTokens, boolean stream) {
        return snapshotJson(scenario, route, maxOutputTokens, stream, null);
    }

    /**
     * 配置快照要带上联网结论（ADR-0012 决策 4）：请求到的档位、实际生效与否、用的哪种形状。
     * Qwen 这类网关超限是"不报错也不搜"的，不在每次调用里记 effective，事后根本无法判断那一场到底联网没有。
     */
    public String snapshotJson(AiScenario scenario, AiScenarioRouter.ResolvedRoute route,
                               int maxOutputTokens, boolean stream, WebSearchDecision webSearch) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("scenario", scenario.name());
        snapshot.put("source", route.source());
        snapshot.put("failoverEnabled", route.failoverEnabled());
        snapshot.put("primary", connectionSummary(route.primary()));
        if (route.backup() != null) {
            snapshot.put("backup", connectionSummary(route.backup()));
        }
        snapshot.put("maxOutputTokens", maxOutputTokens);
        snapshot.put("stream", stream);
        snapshot.put("webSearchDialect", route.primary().webSearchDialect().name());
        if (webSearch != null) {
            snapshot.put("webSearchApplied", webSearch.applied());
            snapshot.put("webSearchDegraded", webSearch.degraded());
            if (webSearch.reason() != null) {
                snapshot.put("webSearchNote", webSearch.reason());
            }
        }
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    /**
     * 成本 = 非缓存输入 × 输入单价 + 缓存命中输入 × 缓存单价 + 输出 × 输出单价（单位：每百万 token）。
     * 单价缺失或 token 缺失都返回 null——算不出就是算不出，不返回 0 冒充免费。
     *
     * <p>缓存单价没配时，这部分按常规输入价计——宁可高估也不低估：用户看到「花费比真实账单多」
     * 会去查单价配置，看到「比账单少」则会误以为便宜（与 unpricedCalls 的诚实口径同一动机）。
     */
    public static java.math.BigDecimal cost(AiConnectionRuntimeConfig connection,
                                            com.orbitworkbench.ai.application.AiUsage usage) {
        if (connection == null || usage == null) {
            return null;
        }
        return cost(connection, usage.inputTokens(), usage.outputTokens(),
                usage.cachedInputTokens());
    }

    /** 旧口径三参版：无缓存明细（上游没报 / 非缓存场景）仍可用。 */
    public static java.math.BigDecimal cost(AiConnectionRuntimeConfig connection,
                                            Integer inputTokens, Integer outputTokens) {
        return cost(connection, inputTokens, outputTokens, null);
    }

    public static java.math.BigDecimal cost(AiConnectionRuntimeConfig connection,
                                            Integer inputTokens, Integer outputTokens,
                                            Integer cachedInputTokens) {
        if (connection == null || (inputTokens == null && outputTokens == null)) {
            return null;
        }
        java.math.BigDecimal inputPrice = connection.inputPricePerMillion();
        java.math.BigDecimal outputPrice = connection.outputPricePerMillion();
        if (inputPrice == null && outputPrice == null) {
            return null;
        }
        java.math.BigDecimal million = java.math.BigDecimal.valueOf(1_000_000L);
        java.math.BigDecimal total = java.math.BigDecimal.ZERO;
        boolean any = false;
        // cached 是 input 的子集：先按缓存价扣出命中部分，剩余按常规输入价；缓存价未配则整体按输入价。
        int cached = cachedInputTokens == null ? 0 : Math.max(0, cachedInputTokens);
        if (inputTokens != null && inputPrice != null) {
            int regular = Math.max(0, inputTokens - cached);
            total = total.add(inputPrice.multiply(java.math.BigDecimal.valueOf(regular)));
            any = true;
        }
        if (cached > 0 && inputTokens != null && inputTokens > 0) {
            java.math.BigDecimal cachePrice =
                    connection.cachedInputPricePerMillion() == null ? inputPrice
                            : connection.cachedInputPricePerMillion();
            if (cachePrice != null) {
                total = total.add(cachePrice.multiply(java.math.BigDecimal.valueOf(cached)));
                any = true;
            }
        }
        if (outputTokens != null && outputPrice != null) {
            total = total.add(outputPrice.multiply(java.math.BigDecimal.valueOf(outputTokens)));
            any = true;
        }
        return any ? total.divide(million, 6, java.math.RoundingMode.HALF_UP) : null;
    }

    private Map<String, Object> connectionSummary(AiConnectionRuntimeConfig connection) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("connectionId", connection.connectionId());
        summary.put("name", connection.connectionName());
        summary.put("protocol", connection.protocol());
        summary.put("model", connection.modelName());
        summary.put("timeoutMs", connection.timeoutMs());
        if (connection.inputPricePerMillion() != null || connection.outputPricePerMillion() != null) {
            summary.put("pricePerMillionIn", connection.inputPricePerMillion());
            summary.put("pricePerMillionOut", connection.outputPricePerMillion());
            summary.put("pricePerMillionCachedIn", connection.cachedInputPricePerMillion());
        }
        return summary;
    }

    private String snapshot(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_SNAPSHOT_CHARS ? value : null;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
