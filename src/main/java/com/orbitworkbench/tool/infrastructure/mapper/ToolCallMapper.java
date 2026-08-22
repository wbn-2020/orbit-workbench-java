package com.orbitworkbench.tool.infrastructure.mapper;

import com.orbitworkbench.tool.domain.ToolCallRecord;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ToolCallMapper {

    void insert(ToolCallRecord call);

    ToolCallRecord findById(@Param("id") Long id);

    ToolCallRecord findByCallKey(@Param("callKey") String callKey);

    List<ToolCallRecord> findByRunId(@Param("runId") Long runId,
                                     @Param("offset") long offset,
                                     @Param("limit") int limit);

    List<ToolCallRecord> findByWorkflowRunId(@Param("workflowRunId") Long workflowRunId,
                                             @Param("offset") long offset,
                                             @Param("limit") int limit);

    long countByRunId(@Param("runId") Long runId);

    long countByWorkflowRunId(@Param("workflowRunId") Long workflowRunId);

    int markRunning(@Param("id") Long id,
                    @Param("startedAt") Instant startedAt);

    int markSucceeded(@Param("id") Long id,
                      @Param("resultSnapshotRef") String resultSnapshotRef,
                      @Param("resultSummary") String resultSummary,
                      @Param("resultSizeBytes") int resultSizeBytes,
                      @Param("finishedAt") Instant finishedAt);

    int markFailed(@Param("id") Long id,
                   @Param("errorCode") String errorCode,
                   @Param("errorSummary") String errorSummary,
                   @Param("finishedAt") Instant finishedAt);

    int markCancelled(@Param("id") Long id,
                      @Param("finishedAt") Instant finishedAt);

    int cancelRunningByRunId(@Param("runId") Long runId,
                             @Param("finishedAt") Instant finishedAt);
}
