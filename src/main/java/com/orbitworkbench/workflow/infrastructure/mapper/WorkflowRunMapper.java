package com.orbitworkbench.workflow.infrastructure.mapper;

import com.orbitworkbench.workflow.domain.WorkflowRunRecord;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface WorkflowRunMapper {

    void insert(WorkflowRunRecord run);

    WorkflowRunRecord findById(@Param("id") Long id);

    WorkflowRunRecord findByIdForUpdate(@Param("id") Long id);

    List<WorkflowRunRecord> findByWorkflowDefinitionId(
            @Param("workflowDefinitionId") Long workflowDefinitionId,
            @Param("offset") int offset,
            @Param("limit") int limit);

    long countByWorkflowDefinitionId(
            @Param("workflowDefinitionId") Long workflowDefinitionId);

    int updateStatus(@Param("id") Long id,
                     @Param("fromStatus") String fromStatus,
                     @Param("toStatus") String toStatus,
                     @Param("errorCode") String errorCode,
                     @Param("errorSummary") String errorSummary,
                     @Param("finishedAt") Instant finishedAt,
                     @Param("updatedAt") Instant updatedAt);

    int markRunning(@Param("id") Long id,
                    @Param("startedAt") Instant startedAt);

    int updateCurrentNode(@Param("id") Long id,
                          @Param("currentNodeKey") String currentNodeKey,
                          @Param("updatedAt") Instant updatedAt);

    int markSucceeded(@Param("id") Long id,
                      @Param("outputJson") String outputJson,
                      @Param("finishedAt") Instant finishedAt,
                      @Param("updatedAt") Instant updatedAt);

    int markFailed(@Param("id") Long id,
                   @Param("errorCode") String errorCode,
                   @Param("errorSummary") String errorSummary,
                   @Param("finishedAt") Instant finishedAt,
                   @Param("updatedAt") Instant updatedAt);
}
