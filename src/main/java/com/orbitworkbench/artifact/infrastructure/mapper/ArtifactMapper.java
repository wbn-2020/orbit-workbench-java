package com.orbitworkbench.artifact.infrastructure.mapper;

import com.orbitworkbench.artifact.domain.ArtifactRecord;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ArtifactMapper {

    void insert(ArtifactRecord artifact);

    ArtifactRecord findById(@Param("id") Long id);

    ArtifactRecord findByIdForUpdate(@Param("id") Long id);

    List<ArtifactRecord> findPage(@Param("workspaceId") Long workspaceId,
                                  @Param("taskId") Long taskId,
                                  @Param("offset") long offset,
                                  @Param("limit") int limit);

    long countPage(@Param("workspaceId") Long workspaceId,
                   @Param("taskId") Long taskId);

    int countSourceContext(@Param("workspaceId") Long workspaceId,
                           @Param("taskId") Long taskId,
                           @Param("sourceRunId") Long sourceRunId);

    int updateCurrentVersion(@Param("id") Long id,
                             @Param("title") String title,
                             @Param("currentVersionId") Long currentVersionId,
                             @Param("updatedAt") java.time.Instant updatedAt);
}
