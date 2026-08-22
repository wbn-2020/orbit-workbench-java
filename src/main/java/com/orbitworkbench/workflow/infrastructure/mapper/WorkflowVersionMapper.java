package com.orbitworkbench.workflow.infrastructure.mapper;

import com.orbitworkbench.workflow.domain.WorkflowVersionRecord;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface WorkflowVersionMapper {

    void insert(WorkflowVersionRecord version);

    WorkflowVersionRecord findById(@Param("id") Long id);

    WorkflowVersionRecord findByIdForUpdate(@Param("id") Long id);

    List<WorkflowVersionRecord> findByDefinitionId(
            @Param("workflowDefinitionId") Long workflowDefinitionId);

    int nextVersionNumber(@Param("workflowDefinitionId") Long workflowDefinitionId);

    int updateDraftMetadata(@Param("id") Long id,
                            @Param("workflowDefinitionId") Long workflowDefinitionId,
                            @Param("metadataJson") String metadataJson,
                            @Param("updatedAt") Instant updatedAt);

    int publishDraft(@Param("id") Long id,
                     @Param("workflowDefinitionId") Long workflowDefinitionId,
                     @Param("now") Instant now);

    int disableAll(@Param("workflowDefinitionId") Long workflowDefinitionId,
                   @Param("now") Instant now);
}
