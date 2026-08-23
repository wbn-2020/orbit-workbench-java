package com.orbitworkbench.artifact.infrastructure.mapper;

import com.orbitworkbench.artifact.domain.ArtifactVersionRecord;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ArtifactVersionMapper {

    void insert(ArtifactVersionRecord version);

    List<ArtifactVersionRecord> findByArtifactId(@Param("artifactId") Long artifactId);

    ArtifactVersionRecord findLatestByArtifactId(@Param("artifactId") Long artifactId);

    ArtifactVersionRecord findByArtifactIdAndId(@Param("artifactId") Long artifactId,
                                                @Param("id") Long id);
}
