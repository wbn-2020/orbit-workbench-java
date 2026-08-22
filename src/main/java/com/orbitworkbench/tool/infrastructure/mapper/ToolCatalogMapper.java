package com.orbitworkbench.tool.infrastructure.mapper;

import com.orbitworkbench.tool.domain.ToolCatalogRecord;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ToolCatalogMapper {

    List<ToolCatalogRecord> findAll(@Param("status") String status);

    ToolCatalogRecord findById(@Param("id") Long id);

    ToolCatalogRecord findByIdForUpdate(@Param("id") Long id);

    int updateStatus(@Param("id") Long id,
                     @Param("expectedStatus") String expectedStatus,
                     @Param("status") String status,
                     @Param("updatedAt") Instant updatedAt);

    int bumpLockVersion(@Param("id") Long id,
                        @Param("expectedLockVersion") Long expectedLockVersion,
                        @Param("now") Instant now);

    int setPublishedVersion(@Param("id") Long id,
                            @Param("versionId") Long versionId,
                            @Param("expectedLockVersion") Long expectedLockVersion,
                            @Param("now") Instant now);
}
