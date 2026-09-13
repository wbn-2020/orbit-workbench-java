package com.orbitworkbench.aiconnection.infrastructure.mapper;

import com.orbitworkbench.aiconnection.domain.CallAuditRecord;
import com.orbitworkbench.aiconnection.domain.ScenarioRouteRecord;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * AI 场景路由与调用审计的数据访问。两张表均按 {@code user_id} 隔离。
 */
public interface AiScenarioMapper {

    List<ScenarioRouteRecord> listRoutes(@Param("userId") Long userId);

    ScenarioRouteRecord findRoute(
            @Param("userId") Long userId,
            @Param("scenarioCode") String scenarioCode);

    int insertRoute(ScenarioRouteRecord record);

    int updateRoute(ScenarioRouteRecord record);

    int deleteRoute(
            @Param("userId") Long userId,
            @Param("scenarioCode") String scenarioCode);

    int insertAudit(CallAuditRecord record);

    int finishAudit(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("status") String status,
            @Param("errorCode") String errorCode,
            @Param("latencyMs") Integer latencyMs,
            @Param("requestChars") Integer requestChars,
            @Param("responseChars") Integer responseChars,
            @Param("inputTokens") Integer inputTokens,
            @Param("outputTokens") Integer outputTokens,
            @Param("cachedInputTokens") Integer cachedInputTokens,
            @Param("reasoningOutputTokens") Integer reasoningOutputTokens,
            @Param("costAmount") java.math.BigDecimal costAmount,
            @Param("usedConnectionId") Long usedConnectionId,
            @Param("backupAttempted") boolean backupAttempted,
            @Param("finishedAt") Instant finishedAt);

    /** 时间窗内的总量：调用数、成功/失败、token 合计、已计价金额、未计价调用数。 */
    com.orbitworkbench.aiconnection.domain.UsageSummary sumUsage(
            @Param("userId") Long userId,
            @Param("from") java.time.Instant from);

    /** 按日分组（DATE(created_at)），升序。 */
    List<com.orbitworkbench.aiconnection.domain.UsageRow> usageByDay(
            @Param("userId") Long userId,
            @Param("from") java.time.Instant from);

    /** 按场景分组，调用数降序。 */
    List<com.orbitworkbench.aiconnection.domain.UsageRow> usageByScenario(
            @Param("userId") Long userId,
            @Param("from") java.time.Instant from);

    /** 按模型分组（model_profile 每连接一行），调用数降序。 */
    List<com.orbitworkbench.aiconnection.domain.UsageRow> usageByModel(
            @Param("userId") Long userId,
            @Param("from") java.time.Instant from);

    List<CallAuditRecord> listAudits(
            @Param("userId") Long userId,
            @Param("scenarioCode") String scenarioCode,
            @Param("limit") int limit,
            @Param("offset") int offset);

    long countAudits(
            @Param("userId") Long userId,
            @Param("scenarioCode") String scenarioCode);

    /** 单次调用下钻：按 id 取一行（含配置快照），必须带 user_id 归属过滤。 */
    CallAuditRecord findAuditById(
            @Param("id") Long id,
            @Param("userId") Long userId);

    /**
     * 保留策略清理（V43）：删除该用户早于 cutoff 的已结算审计行。
     * 只删 status != 'RUNNING'——正在跑的调用还没有结果，删了会留下无法收尾的孤儿。
     * 返回删除行数。
     */
    int purgeAuditsBefore(
            @Param("userId") Long userId,
            @Param("cutoff") java.time.Instant cutoff);
}
