package com.orbitworkbench.aiconnection.api;

import com.orbitworkbench.aiconnection.domain.AiScenario;
import com.orbitworkbench.aiconnection.domain.CallAuditRecord;
import com.orbitworkbench.aiconnection.domain.ScenarioRouteRecord;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** AI 场景路由与调用审计的传输对象。 */
public final class AiScenarioDtos {

    private AiScenarioDtos() {
    }

    /** 场景码转中文标签；未知码原样返回，不吞掉信息。列表与详情共用。 */
    private static String scenarioLabel(String code) {
        return AiScenario.parse(code).map(AiScenario::label).orElse(code);
    }

    public record UpsertRouteRequest(
            @NotNull(message = "必须选择主用账户") Long primaryConnectionId,
            Long backupConnectionId,
            boolean failoverEnabled) {
    }

    /**
     * 场景当前生效的账户配置。
     *
     * @param source ROUTE 表示已显式配置，DEFAULT 表示回退“第一个已启用账户”
     */
    public record ScenarioRouteResponse(
            String scenario,
            String scenarioLabel,
            String source,
            Long primaryConnectionId,
            String primaryConnectionName,
            Long backupConnectionId,
            String backupConnectionName,
            boolean failoverEnabled,
            int version,
            Instant updatedAt) {

        public static ScenarioRouteResponse configured(ScenarioRouteRecord route, String scenario,
                                                       String label) {
            return new ScenarioRouteResponse(scenario, label, "ROUTE",
                    route.getPrimaryConnectionId(), route.getPrimaryConnectionName(),
                    route.getBackupConnectionId(), route.getBackupConnectionName(),
                    route.isFailoverEnabled(), route.getVersion(), route.getUpdatedAt());
        }

        public static ScenarioRouteResponse unconfigured(Long connectionId, String connectionName,
                                                         String scenario, String label) {
            return new ScenarioRouteResponse(scenario, label, "DEFAULT",
                    connectionId, connectionName, null, null, false, 0, null);
        }
    }

    /**
     * 单次调用下钻（V43）：列表列 + 落库的非敏感配置快照。
     * 快照里带 primary/backup 的账户名、模型、联网结论与单价，
     * 前端据此解释「这次为什么是这个结果」，不需要再查一遍连接表。
     */
    public record CallAuditDetailResponse(
            Long id,
            String scenario,
            String scenarioLabel,
            Long primaryConnectionId,
            Long usedConnectionId,
            String usedConnectionName,
            boolean backupAttempted,
            String status,
            String errorCode,
            Integer latencyMs,
            int requestChars,
            int responseChars,
            Integer inputTokens,
            Integer outputTokens,
            Integer cachedInputTokens,
            Integer reasoningOutputTokens,
            java.math.BigDecimal costAmount,
            /** 配置快照 JSON（路由/模型/联网结论/单价），非敏感；旧行可能为 null。 */
            String configurationSnapshotJson,
            Instant createdAt,
            Instant finishedAt) {

        public static CallAuditDetailResponse from(CallAuditRecord record) {
            return new CallAuditDetailResponse(record.getId(), record.getScenarioCode(),
                    AiScenarioDtos.scenarioLabel(record.getScenarioCode()), record.getPrimaryConnectionId(),
                    record.getUsedConnectionId(), record.getUsedConnectionName(),
                    record.isBackupAttempted(), record.getStatus(), record.getErrorCode(),
                    record.getLatencyMs(), record.getRequestChars(), record.getResponseChars(),
                    record.getInputTokens(), record.getOutputTokens(),
                    record.getCachedInputTokens(), record.getReasoningOutputTokens(),
                    record.getCostAmount(), record.getConfigurationSnapshotJson(),
                    record.getCreatedAt(), record.getFinishedAt());
        }
    }

    public record CallAuditResponse(
            Long id,
            String scenario,
            String scenarioLabel,
            Long primaryConnectionId,
            Long usedConnectionId,
            String usedConnectionName,
            boolean backupAttempted,
            String status,
            String errorCode,
            Integer latencyMs,
            int requestChars,
            int responseChars,
            Integer inputTokens,
            Integer outputTokens,
            Integer cachedInputTokens,
            Integer reasoningOutputTokens,
            java.math.BigDecimal costAmount,
            Instant createdAt,
            Instant finishedAt) {

        public static CallAuditResponse from(CallAuditRecord record) {
            return new CallAuditResponse(record.getId(), record.getScenarioCode(),
                    AiScenarioDtos.scenarioLabel(record.getScenarioCode()), record.getPrimaryConnectionId(),
                    record.getUsedConnectionId(), record.getUsedConnectionName(),
                    record.isBackupAttempted(), record.getStatus(), record.getErrorCode(),
                    record.getLatencyMs(), record.getRequestChars(), record.getResponseChars(),
                    record.getInputTokens(), record.getOutputTokens(),
                    record.getCachedInputTokens(), record.getReasoningOutputTokens(),
                    record.getCostAmount(),
                    record.getCreatedAt(), record.getFinishedAt());
        }
    }
}
