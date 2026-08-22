package com.orbitworkbench.tool.infrastructure.mapper;

import com.orbitworkbench.tool.domain.ToolVersionRecord;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ToolVersionMapper {

    void insert(ToolVersionRecord version);

    List<ToolVersionRecord> findByCatalogId(@Param("toolCatalogId") Long toolCatalogId);

    ToolVersionRecord findById(@Param("id") Long id);

    ToolVersionRecord findByIdForUpdate(@Param("id") Long id);

    ToolVersionRecord findDraftForUpdate(@Param("toolCatalogId") Long toolCatalogId);

    int updateDraft(ToolVersionRecord version);

    int publishDraft(@Param("toolCatalogId") Long toolCatalogId,
                     @Param("versionId") Long versionId,
                     @Param("now") java.time.Instant now);

    int nextVersionNumber(@Param("toolCatalogId") Long toolCatalogId);

    ToolVersionRecord findPublishedByCodeAndVersion(
            @Param("toolCode") String toolCode,
            @Param("versionNumber") Integer versionNumber);

    ToolVersionRecord findPublishedLatestByCode(@Param("toolCode") String toolCode);
}
