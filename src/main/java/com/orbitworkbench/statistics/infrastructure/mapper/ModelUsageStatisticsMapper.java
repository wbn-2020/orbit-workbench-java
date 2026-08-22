package com.orbitworkbench.statistics.infrastructure.mapper;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ModelUsageStatisticsMapper {

    ModelUsageRow summarize(@Param("workspaceId") Long workspaceId,
                            @Param("fromInclusive") Instant fromInclusive,
                            @Param("toExclusive") Instant toExclusive);

    List<ModelUsageRow> summarizeByConnection(
            @Param("workspaceId") Long workspaceId,
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive);

    List<ModelUsageRow> summarizeByModel(
            @Param("workspaceId") Long workspaceId,
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive);

    List<ModelUsageRow> summarizeByDay(
            @Param("workspaceId") Long workspaceId,
            @Param("fromInclusive") Instant fromInclusive,
            @Param("toExclusive") Instant toExclusive);
}
