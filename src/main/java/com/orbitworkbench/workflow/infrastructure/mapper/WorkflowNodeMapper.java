package com.orbitworkbench.workflow.infrastructure.mapper;

import com.orbitworkbench.workflow.domain.WorkflowNodeRecord;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface WorkflowNodeMapper {

    void insert(WorkflowNodeRecord node);

    List<WorkflowNodeRecord> findByVersionId(@Param("workflowVersionId") Long workflowVersionId);

    int deleteByVersionId(@Param("workflowVersionId") Long workflowVersionId);
}
