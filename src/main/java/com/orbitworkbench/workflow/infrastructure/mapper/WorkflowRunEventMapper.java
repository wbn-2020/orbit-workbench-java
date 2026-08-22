package com.orbitworkbench.workflow.infrastructure.mapper;

import com.orbitworkbench.workflow.domain.WorkflowRunEventRecord;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface WorkflowRunEventMapper {

    void insert(WorkflowRunEventRecord event);

    int incrementSequence(@Param("workflowRunId") Long workflowRunId);

    Long currentSequence(@Param("workflowRunId") Long workflowRunId);

    List<WorkflowRunEventRecord> findAfterSequence(
            @Param("workflowRunId") Long workflowRunId,
            @Param("afterSequence") long afterSequence,
            @Param("limit") int limit);
}
