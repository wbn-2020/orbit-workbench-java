package com.orbitworkbench.statistics.api;

import java.time.LocalDate;
import java.util.List;

public final class StatisticsDtos {

    private StatisticsDtos() {
    }

    public record ModelUsageResponse(
            ModelUsageMetrics summary,
            List<ModelUsageByConnection> byConnection,
            List<ModelUsageByModel> byModel,
            List<ModelUsageByDay> byDay
    ) {
    }

    public record ModelUsageMetrics(
            long callCount,
            long successCount,
            long failureCount,
            long inputTokens,
            long outputTokens,
            long averageLatencyMs
    ) {
    }

    public record ModelUsageByConnection(
            Long connectionId,
            String connectionName,
            long callCount,
            long successCount,
            long failureCount,
            long inputTokens,
            long outputTokens,
            long averageLatencyMs
    ) {
    }

    public record ModelUsageByModel(
            Long modelProfileId,
            String modelName,
            long callCount,
            long successCount,
            long failureCount,
            long inputTokens,
            long outputTokens,
            long averageLatencyMs
    ) {
    }

    public record ModelUsageByDay(
            LocalDate date,
            long callCount,
            long successCount,
            long failureCount,
            long inputTokens,
            long outputTokens,
            long averageLatencyMs
    ) {
    }
}
