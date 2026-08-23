package com.orbitworkbench.content.infrastructure.mapper;

import com.orbitworkbench.content.domain.ContentProjectMaterialRecord;
import com.orbitworkbench.content.domain.ContentProjectRecord;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ContentProjectMapper {

    List<ContentProjectRecord> findPage(@Param("workspaceId") Long workspaceId,
                                        @Param("offset") int offset,
                                        @Param("limit") int limit);

    long countPage(@Param("workspaceId") Long workspaceId);

    ContentProjectRecord findById(@Param("id") Long id);

    ContentProjectRecord findByIdForUpdate(@Param("id") Long id);

    void insert(ContentProjectRecord project);

    int update(@Param("project") ContentProjectRecord project,
               @Param("expectedVersion") Long expectedVersion);

    List<ContentProjectMaterialRecord> findMaterials(@Param("projectId") Long projectId);

    ContentProjectMaterialRecord findMaterialByIdForUpdate(
            @Param("projectId") Long projectId,
            @Param("materialId") Long materialId);

    void insertMaterial(ContentProjectMaterialRecord material);

    int deleteMaterial(@Param("projectId") Long projectId,
                       @Param("materialId") Long materialId);
}
