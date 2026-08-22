package com.orbitworkbench.artifactexport.infrastructure.mapper;

import com.orbitworkbench.artifactexport.domain.ArtifactExportRecord;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ArtifactExportMapper {

    ArtifactExportRecord findSourceForUpdate(@Param("artifactId") Long artifactId,
                                             @Param("artifactVersionId") Long artifactVersionId);

    ArtifactExportRecord findByExportKeyForUpdate(@Param("exportKey") String exportKey);

    int insertPending(ArtifactExportRecord export);

    ArtifactExportRecord findById(@Param("id") Long id);

    ArtifactExportRecord findDownloadableById(@Param("id") Long id);

    List<ArtifactExportRecord> findByArtifactId(@Param("artifactId") Long artifactId,
                                                @Param("limit") int limit);

    int countActiveArtifact(@Param("artifactId") Long artifactId);

    int markRunning(@Param("id") Long id,
                    @Param("updatedAt") Instant updatedAt);

    int markSucceeded(@Param("id") Long id,
                      @Param("storageRef") String storageRef,
                      @Param("sizeBytes") long sizeBytes,
                      @Param("contentHash") String contentHash,
                      @Param("finishedAt") Instant finishedAt);

    int markFailed(@Param("id") Long id,
                   @Param("errorCode") String errorCode,
                   @Param("errorSummary") String errorSummary,
                   @Param("finishedAt") Instant finishedAt);
}
