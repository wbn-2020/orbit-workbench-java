package com.orbitworkbench.aiconnection.application;

import com.orbitworkbench.aiconnection.domain.UsageRow;
import com.orbitworkbench.aiconnection.domain.UsageSummary;
import com.orbitworkbench.aiconnection.infrastructure.mapper.AiScenarioMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 用量与费用账本（只读聚合）。
 *
 * <p>成本只在有单价且上游报了 token 时才算得出来；算不出来的调用以
 * {@code unpricedCalls} 计数如实返回，界面据此说明金额覆盖范围——
 * 不会把「不知道」渲染成 0，让用户以为免费。
 */
@Service
public class AiUsageService {

    private static final int DEFAULT_DAYS = 30;
    private static final int MAX_DAYS = 365;

    /** 可选的统计窗口；null 表示默认 30 天。 */
    public record Window(int days, Instant from, List<String> dayKeys) {}

    public record UsageResponse(
            int days,
            Instant from,
            UsageSummary total,
            List<RowView> byDay,
            List<RowView> byScenario,
            List<RowView> byModel,
            boolean anyPricingConfigured) {}

    public record RowView(
            String key,
            long calls,
            long succeeded,
            long failed,
            long inputTokens,
            long outputTokens,
            long cachedInputTokens,
            long reasoningOutputTokens,
            BigDecimal costAmount,
            long unpricedCalls) {

        static RowView from(UsageRow row) {
            return new RowView(row.getKey(), row.getCalls(), row.getSucceeded(), row.getFailed(),
                    row.getInputTokens(), row.getOutputTokens(),
                    row.getCachedInputTokens(), row.getReasoningOutputTokens(),
                    row.getCostAmount(), row.getUnpricedCalls());
        }
    }

    private final AiScenarioMapper mapper;

    public AiUsageService(AiScenarioMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public UsageResponse usage(Long userId, Integer daysParam, boolean anyPricingConfigured) {
        int days = normalizeDays(daysParam);
        Instant from = Instant.now().minus(days, ChronoUnit.DAYS);

        UsageSummary total = mapper.sumUsage(userId, from);
        if (total == null) {
            total = new UsageSummary();
        }
        List<RowView> byDay = mapper.usageByDay(userId, from).stream().map(RowView::from).toList();
        List<RowView> byScenario = mapper.usageByScenario(userId, from).stream().map(RowView::from).toList();
        List<RowView> byModel = mapper.usageByModel(userId, from).stream().map(RowView::from).toList();

        return new UsageResponse(days, from, total, byDay, byScenario, byModel, anyPricingConfigured);
    }

    private static int normalizeDays(Integer raw) {
        if (raw == null) {
            return DEFAULT_DAYS;
        }
        if (raw < 1) {
            return DEFAULT_DAYS;
        }
        return Math.min(raw, MAX_DAYS);
    }
}
