package com.orbitworkbench.workflow.infrastructure.mapper;

import com.orbitworkbench.workflow.domain.WorkflowDefinitionRecord;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface WorkflowDefinitionMapper {

    void insert(WorkflowDefinitionRecord definition);

    WorkflowDefinitionRecord findById(@Param("id") Long id);

    WorkflowDefinitionRecord findByIdForUpdate(@Param("id") Long id);

    WorkflowDefinitionRecord findByWorkspaceCode(@Param("workspaceId") Long workspaceId,
                                                 @Param("code") String code);

    List<WorkflowDefinitionRecord> findByWorkspaceId(@Param("workspaceId") Long workspaceId);

    List<WorkflowDefinitionRecord> findPageByWorkspaceId(
            @Param("workspaceId") Long workspaceId,
            @Param("status") String status,
            @Param("offset") int offset,
            @Param("limit") int limit);

    long countByWorkspaceId(@Param("workspaceId") Long workspaceId,
                            @Param("status") String status);

    int updateMetadata(@Param("definition") WorkflowDefinitionRecord definition,
                       @Param("expectedVersion") Long expectedVersion);

    int setPublishedVersion(@Param("id") Long id,
                            @Param("versionId") Long versionId,
                            @Param("now") Instant now);

    int disable(@Param("id") Long id,
                @Param("now") Instant now);
}
