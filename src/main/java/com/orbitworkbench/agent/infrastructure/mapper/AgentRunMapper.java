package com.orbitworkbench.agent.infrastructure.mapper;

import com.orbitworkbench.agent.domain.AgentRunRecord;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface AgentRunMapper {

    void insert(AgentRunRecord run);

    AgentRunRecord findById(@Param("id") Long id);

    AgentRunRecord findByIdForUpdate(@Param("id") Long id);

    List<AgentRunRecord> findByTaskId(@Param("taskId") Long taskId);

    int markRunning(@Param("id") Long id,
                    @Param("now") java.time.Instant now);

    int markRunningFromPaused(@Param("id") Long id,
                              @Param("now") java.time.Instant now);

    int touchHeartbeat(@Param("id") Long id,
                       @Param("now") java.time.Instant now);

    int updateStatus(@Param("id") Long id,
                     @Param("fromStatus") String fromStatus,
                     @Param("toStatus") String toStatus,
                     @Param("errorCode") String errorCode,
                     @Param("errorSummary") String errorSummary,
                     @Param("finishedAt") java.time.Instant finishedAt);

    int requestCancel(@Param("id") Long id,
                      @Param("fromStatuses") List<String> fromStatuses);

    List<Long> findInterruptedIds();

    int markRecoveryRequired(@Param("id") Long id);

    AgentRunRecord findLatestRetryByRunId(@Param("runId") Long runId);
}
