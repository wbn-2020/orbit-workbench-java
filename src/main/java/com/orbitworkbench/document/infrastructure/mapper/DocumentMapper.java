package com.orbitworkbench.document.infrastructure.mapper;

import com.orbitworkbench.document.domain.DocumentRecord;
import com.orbitworkbench.document.domain.StorageCleanupFailureRecord;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface DocumentMapper {

    void insert(DocumentRecord document);

    DocumentRecord findById(@Param("id") Long id);

    DocumentRecord findByIdForUpdate(@Param("id") Long id);

    DocumentRecord findByWorkspaceAndHash(@Param("workspaceId") Long workspaceId,
                                          @Param("contentHash") String contentHash);

    List<DocumentRecord> findPage(@Param("workspaceId") Long workspaceId,
                                  @Param("offset") long offset,
                                  @Param("limit") int limit);

    long countPage(@Param("workspaceId") Long workspaceId);

    List<DocumentRecord> findByIds(@Param("documentIds") List<Long> documentIds);

    int countTaskReferences(@Param("documentId") Long documentId);

    int countActiveWorkspace(@Param("workspaceId") Long workspaceId);

    Long lockActiveWorkspace(@Param("workspaceId") Long workspaceId);

    int deleteById(@Param("id") Long id);

    void insertCleanupFailure(StorageCleanupFailureRecord failure);

    List<StorageCleanupFailureRecord> findCleanupFailurePage(
            @Param("status") String status,
            @Param("offset") long offset,
            @Param("limit") int limit);

    long countCleanupFailures(@Param("status") String status);

    StorageCleanupFailureRecord findCleanupFailureByIdForUpdate(@Param("id") Long id);

    int resolveCleanupFailure(@Param("id") Long id,
                              @Param("resolvedAt") java.time.Instant resolvedAt);

    int recordCleanupRetryFailure(@Param("id") Long id,
                                  @Param("failureType") String failureType,
                                  @Param("failureSummary") String failureSummary,
                                  @Param("retriedAt") java.time.Instant retriedAt);
}
