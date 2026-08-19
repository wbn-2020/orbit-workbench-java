package com.orbitworkbench.agent.infrastructure.mapper;

import com.orbitworkbench.agent.domain.ModelCallRecord;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ModelCallMapper {

    void insert(ModelCallRecord call);

    List<ModelCallRecord> findByRunId(@Param("runId") Long runId);

    int updateFinished(@Param("id") Long id,
                       @Param("status") String status,
                       @Param("finishedAt") java.time.Instant finishedAt,
                       @Param("providerRequestId") String providerRequestId,
                       @Param("inputTokenCount") Integer inputTokenCount,
                       @Param("outputTokenCount") Integer outputTokenCount,
                       @Param("errorCode") String errorCode,
                       @Param("errorSummary") String errorSummary);

    int finishRunningByRunId(@Param("runId") Long runId,
                             @Param("status") String status,
                             @Param("finishedAt") java.time.Instant finishedAt,
                             @Param("errorCode") String errorCode,
                             @Param("errorSummary") String errorSummary);
}
