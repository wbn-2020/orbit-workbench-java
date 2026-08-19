package com.orbitworkbench.workspace.infrastructure.mapper;

import com.orbitworkbench.workspace.domain.WorkspaceRecord;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface WorkspaceMapper {
    void insert(WorkspaceRecord workspace);
    WorkspaceRecord findById(@Param("id") Long id);
    WorkspaceRecord findByIdForUpdate(@Param("id") Long id);
    List<WorkspaceRecord> findAll();
    int update(WorkspaceRecord workspace);
    int softDelete(@Param("id") Long id);
    int countAll();
    int countTasks(@Param("workspaceId") Long workspaceId);
    int countDocuments(@Param("workspaceId") Long workspaceId);
    int countArtifacts(@Param("workspaceId") Long workspaceId);
}
