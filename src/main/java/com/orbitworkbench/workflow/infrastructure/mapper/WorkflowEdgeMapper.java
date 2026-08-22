package com.orbitworkbench.workflow.infrastructure.mapper;

import com.orbitworkbench.workflow.domain.WorkflowEdgeRecord;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface WorkflowEdgeMapper {

    void insert(WorkflowEdgeRecord edge);

    List<WorkflowEdgeRecord> findByVersionId(@Param("workflowVersionId") Long workflowVersionId);

    int deleteByVersionId(@Param("workflowVersionId") Long workflowVersionId);
}
