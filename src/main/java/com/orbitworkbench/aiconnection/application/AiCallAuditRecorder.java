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
        if (auditId == null) {
            return;
        }
        try {
            mapper.finishAudit(auditId, userId, status, truncate(errorCode, MAX_ERROR_CODE_LENGTH),
                    Math.max(0, latencyMs), Math.max(0, requestChars), Math.max(0, responseChars),
                    usedConnectionId, backupAttempted, Instant.now());
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

    private Map<String, Object> connectionSummary(AiConnectionRuntimeConfig connection) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("connectionId", connection.connectionId());
        summary.put("name", connection.connectionName());
        summary.put("protocol", connection.protocol());
        summary.put("model", connection.modelName());
        summary.put("timeoutMs", connection.timeoutMs());
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
