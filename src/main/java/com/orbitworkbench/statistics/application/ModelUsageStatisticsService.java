package com.orbitworkbench.statistics.application;

import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.statistics.api.StatisticsDtos.ModelUsageByConnection;
import com.orbitworkbench.statistics.api.StatisticsDtos.ModelUsageByDay;
import com.orbitworkbench.statistics.api.StatisticsDtos.ModelUsageByModel;
import com.orbitworkbench.statistics.api.StatisticsDtos.ModelUsageMetrics;
import com.orbitworkbench.statistics.api.StatisticsDtos.ModelUsageResponse;
import com.orbitworkbench.statistics.infrastructure.mapper.ModelUsageRow;
import com.orbitworkbench.statistics.infrastructure.mapper.ModelUsageStatisticsMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ModelUsageStatisticsService {

    private final ModelUsageStatisticsMapper modelUsageStatisticsMapper;

    public ModelUsageStatisticsService(ModelUsageStatisticsMapper modelUsageStatisticsMapper) {
        this.modelUsageStatisticsMapper = modelUsageStatisticsMapper;
    }

    @Transactional(readOnly = true)
    public ModelUsageResponse getModelUsage(Long workspaceId, LocalDate from, LocalDate to) {
        validateRange(workspaceId, from, to);
        Instant fromInclusive = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toExclusive = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        ModelUsageRow summary =
                modelUsageStatisticsMapper.summarize(workspaceId, fromInclusive, toExclusive);
        return new ModelUsageResponse(
                toMetrics(summary),
                modelUsageStatisticsMapper
                        .summarizeByConnection(workspaceId, fromInclusive, toExclusive)
                        .stream()
                        .map(this::toConnectionUsage)
                        .toList(),
                modelUsageStatisticsMapper
                        .summarizeByModel(workspaceId, fromInclusive, toExclusive)
                        .stream()
                        .map(this::toModelUsage)
                        .toList(),
                modelUsageStatisticsMapper
                        .summarizeByDay(workspaceId, fromInclusive, toExclusive)
                        .stream()
                        .map(this::toDayUsage)
                        .toList()
        );
    }

    private void validateRange(Long workspaceId, LocalDate from, LocalDate to) {
        if (workspaceId == null || workspaceId <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "workspaceId 必须为正整数");
        }
        if (from == null || to == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "from 和 to 不能为空");
        }
        if (to.isBefore(from)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "to 不能早于 from");
        }
    }

    private ModelUsageMetrics toMetrics(ModelUsageRow row) {
        if (row == null) {
            return new ModelUsageMetrics(0, 0, 0, 0, 0, 0);
        }
        return new ModelUsageMetrics(
                value(row.getCallCount()),
                value(row.getSuccessCount()),
                value(row.getFailureCount()),
                value(row.getInputTokens()),
                value(row.getOutputTokens()),
                value(row.getAverageLatencyMs())
        );
    }

    private ModelUsageByConnection toConnectionUsage(ModelUsageRow row) {
        ModelUsageMetrics metrics = toMetrics(row);
        return new ModelUsageByConnection(
                row.getConnectionId(),
                row.getConnectionName(),
                metrics.callCount(),
                metrics.successCount(),
                metrics.failureCount(),
                metrics.inputTokens(),
                metrics.outputTokens(),
                metrics.averageLatencyMs()
        );
    }

    private ModelUsageByModel toModelUsage(ModelUsageRow row) {
        ModelUsageMetrics metrics = toMetrics(row);
        return new ModelUsageByModel(
                row.getModelProfileId(),
                row.getModelName(),
                metrics.callCount(),
                metrics.successCount(),
                metrics.failureCount(),
                metrics.inputTokens(),
                metrics.outputTokens(),
                metrics.averageLatencyMs()
        );
    }

    private ModelUsageByDay toDayUsage(ModelUsageRow row) {
        ModelUsageMetrics metrics = toMetrics(row);
        return new ModelUsageByDay(
                row.getUsageDate(),
                metrics.callCount(),
                metrics.successCount(),
                metrics.failureCount(),
                metrics.inputTokens(),
                metrics.outputTokens(),
                metrics.averageLatencyMs()
        );
    }

    private long value(Long value) {
        return value == null ? 0 : value;
    }
}
