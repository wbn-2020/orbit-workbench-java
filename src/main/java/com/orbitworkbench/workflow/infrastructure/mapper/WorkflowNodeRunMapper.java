package com.orbitworkbench.workflow.infrastructure.mapper;

import com.orbitworkbench.workflow.domain.WorkflowNodeRunRecord;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface WorkflowNodeRunMapper {

    void insert(WorkflowNodeRunRecord nodeRun);

    List<WorkflowNodeRunRecord> findByRunId(@Param("workflowRunId") Long workflowRunId);

    int markRunning(@Param("id") Long id,
                    @Param("startedAt") java.time.Instant startedAt);

    int markSucceeded(@Param("id") Long id,
                      @Param("outputSummary") String outputSummary,
                      @Param("finishedAt") java.time.Instant finishedAt);

    int markFailed(@Param("id") Long id,
                   @Param("errorCode") String errorCode,
                   @Param("errorSummary") String errorSummary,
                   @Param("finishedAt") java.time.Instant finishedAt);

    int attachToolCall(@Param("id") Long id,
                       @Param("toolCallId") Long toolCallId,
                       @Param("updatedAt") java.time.Instant updatedAt);

    int cancelPendingByRunId(@Param("workflowRunId") Long workflowRunId);
}
