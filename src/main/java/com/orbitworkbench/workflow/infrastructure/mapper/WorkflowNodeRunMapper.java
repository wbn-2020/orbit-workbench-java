package com.orbitworkbench.workflow.infrastructure.mapper;

import com.orbitworkbench.workflow.domain.WorkflowNodeRunRecord;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface WorkflowNodeRunMapper {

    void insert(WorkflowNodeRunRecord nodeRun);

    List<WorkflowNodeRunRecord> findByRunId(@Param("workflowRunId") Long workflowRunId);

    int cancelPendingByRunId(@Param("workflowRunId") Long workflowRunId);
}
