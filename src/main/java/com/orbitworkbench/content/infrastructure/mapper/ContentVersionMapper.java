package com.orbitworkbench.content.infrastructure.mapper;

import com.orbitworkbench.content.domain.ContentVersionRecord;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ContentVersionMapper {

    List<ContentVersionRecord> findByProjectId(@Param("projectId") Long projectId);

    ContentVersionRecord findById(@Param("id") Long id);

    ContentVersionRecord findByIdForUpdate(@Param("id") Long id);

    ContentVersionRecord findByRequestKey(@Param("projectId") Long projectId,
                                          @Param("requestKey") String requestKey);

    ContentVersionRecord findByTaskIdForUpdate(@Param("taskId") Long taskId);

    int findMaxVersionNumber(@Param("projectId") Long projectId);

    void insert(ContentVersionRecord version);

    int attachTask(@Param("id") Long id,
                   @Param("taskId") Long taskId,
                   @Param("runId") Long runId,
                   @Param("status") String status,
                   @Param("updatedAt") Instant updatedAt);

    int markRunning(@Param("taskId") Long taskId,
                    @Param("runId") Long runId,
                    @Param("updatedAt") Instant updatedAt);

    int markSucceeded(@Param("taskId") Long taskId,
                      @Param("runId") Long runId,
                      @Param("artifactId") Long artifactId,
                      @Param("artifactVersionId") Long artifactVersionId,
                      @Param("updatedAt") Instant updatedAt);

    int markFailed(@Param("taskId") Long taskId,
                   @Param("status") String status,
                   @Param("errorCode") String errorCode,
                   @Param("errorSummary") String errorSummary,
                   @Param("updatedAt") Instant updatedAt);
}
