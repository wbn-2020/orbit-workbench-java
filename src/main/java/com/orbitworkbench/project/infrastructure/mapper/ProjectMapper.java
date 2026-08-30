package com.orbitworkbench.project.infrastructure.mapper;

import com.orbitworkbench.project.domain.ProjectFileRecord;
import com.orbitworkbench.project.domain.ProjectRecord;
import com.orbitworkbench.project.domain.ProjectVersionRecord;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ProjectMapper {
    void insertProject(ProjectRecord project);
    ProjectRecord findProjectByIdAndUserId(@Param("projectId") Long projectId,
                                           @Param("userId") Long userId);
    ProjectRecord findProjectByIdAndUserIdForUpdate(@Param("projectId") Long projectId,
                                                    @Param("userId") Long userId);
    List<ProjectRecord> findProjectsByUserId(@Param("userId") Long userId);
    int touchProject(@Param("projectId") Long projectId,
                     @Param("updatedAt") java.time.Instant updatedAt);
    void insertVersion(ProjectVersionRecord version);
    int nextVersionNumber(@Param("projectId") Long projectId);
    ProjectVersionRecord findLatestVersion(@Param("projectId") Long projectId);
    List<ProjectVersionRecord> findVersions(@Param("projectId") Long projectId);
    ProjectVersionRecord findVersionById(@Param("versionId") Long versionId);
    int tryMarkKnowledgeBuilding(@Param("versionId") Long versionId,
                                 @Param("fromStatuses") List<String> fromStatuses,
                                 @Param("updatedAt") java.time.Instant updatedAt);
    int markKnowledgeReady(@Param("versionId") Long versionId,
                           @Param("chunkCount") int chunkCount,
                           @Param("builtAt") java.time.Instant builtAt,
                           @Param("updatedAt") java.time.Instant updatedAt);
    int markKnowledgeFailed(@Param("versionId") Long versionId,
                            @Param("error") String error,
                            @Param("updatedAt") java.time.Instant updatedAt);
    void insertFile(ProjectFileRecord file);
    List<ProjectFileRecord> findFiles(@Param("projectVersionId") Long projectVersionId);
}
