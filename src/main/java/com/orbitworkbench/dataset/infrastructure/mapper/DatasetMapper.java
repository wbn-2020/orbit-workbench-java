package com.orbitworkbench.dataset.infrastructure.mapper;

import com.orbitworkbench.dataset.domain.DatasetColumnRecord;
import com.orbitworkbench.dataset.domain.DatasetProfileRecord;
import com.orbitworkbench.dataset.domain.DatasetRecord;
import com.orbitworkbench.dataset.domain.DatasetSheetRecord;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface DatasetMapper {

    void insertDataset(DatasetRecord dataset);

    DatasetRecord findById(@Param("id") Long id);

    DatasetRecord findByIdForUpdate(@Param("id") Long id);

    DatasetRecord findByWorkspaceAndHash(@Param("workspaceId") Long workspaceId,
                                         @Param("contentHash") String contentHash);

    DatasetRecord findDeletedByWorkspaceAndHashForUpdate(
            @Param("workspaceId") Long workspaceId,
            @Param("contentHash") String contentHash);

    List<DatasetRecord> findPage(@Param("workspaceId") Long workspaceId,
                                 @Param("status") String status,
                                 @Param("format") String format,
                                 @Param("updatedFrom") LocalDateTime updatedFrom,
                                 @Param("updatedToExclusive") LocalDateTime updatedToExclusive,
                                 @Param("offset") long offset,
                                 @Param("limit") int limit);

    long countPage(@Param("workspaceId") Long workspaceId,
                   @Param("status") String status,
                   @Param("format") String format,
                   @Param("updatedFrom") LocalDateTime updatedFrom,
                   @Param("updatedToExclusive") LocalDateTime updatedToExclusive);

    int markParsing(@Param("id") Long id,
                    @Param("updatedAt") Instant updatedAt);

    int updateDocumentStatus(@Param("documentId") Long documentId,
                             @Param("status") String status,
                             @Param("updatedAt") Instant updatedAt);

    void insertSheet(DatasetSheetRecord sheet);

    void insertColumn(DatasetColumnRecord column);

    void insertProfile(DatasetProfileRecord profile);

    List<DatasetSheetRecord> findSheets(@Param("datasetId") Long datasetId);

    DatasetSheetRecord findSheet(@Param("datasetId") Long datasetId,
                                 @Param("sheetId") Long sheetId);

    List<DatasetColumnRecord> findColumns(@Param("datasetId") Long datasetId,
                                          @Param("sheetId") Long sheetId);

    DatasetColumnRecord findColumnForUpdate(@Param("datasetId") Long datasetId,
                                            @Param("sheetId") Long sheetId,
                                            @Param("columnId") Long columnId);

    DatasetProfileRecord findLatestProfile(@Param("datasetId") Long datasetId,
                                           @Param("sheetId") Long sheetId);

    List<String> findSnapshotStorageRefs(@Param("datasetId") Long datasetId);

    List<String> findPreviewStorageRefs(@Param("datasetId") Long datasetId);

    List<String> findProfileStorageRefs(@Param("datasetId") Long datasetId);

    List<Long> findParsingIds();

    int clearActiveSheet(@Param("datasetId") Long datasetId,
                         @Param("updatedAt") Instant updatedAt);

    int deleteProfiles(@Param("datasetId") Long datasetId);

    int deleteColumns(@Param("datasetId") Long datasetId);

    int deleteSheets(@Param("datasetId") Long datasetId);

    int completeParsing(@Param("datasetId") Long datasetId,
                        @Param("activeSheetId") Long activeSheetId,
                        @Param("rowCount") long rowCount,
                        @Param("columnCount") int columnCount,
                        @Param("profileVersion") int profileVersion,
                        @Param("updatedAt") Instant updatedAt);

    int failParsing(@Param("datasetId") Long datasetId,
                    @Param("errorCode") String errorCode,
                    @Param("errorSummary") String errorSummary,
                    @Param("updatedAt") Instant updatedAt);

    int updateColumnType(@Param("columnId") Long columnId,
                         @Param("expectedVersion") long expectedVersion,
                         @Param("effectiveType") String effectiveType,
                         @Param("updatedAt") Instant updatedAt);

    int countAnalysisReferences(@Param("datasetId") Long datasetId);

    int softDelete(@Param("datasetId") Long datasetId,
                   @Param("deletedAt") Instant deletedAt);

    int prepareReactivation(@Param("datasetId") Long datasetId,
                            @Param("updatedAt") Instant updatedAt);

    int reactivateDataset(@Param("datasetId") Long datasetId,
                          @Param("name") String name,
                          @Param("format") String format,
                          @Param("updatedAt") Instant updatedAt);

    int reactivateDocument(@Param("documentId") Long documentId,
                           @Param("name") String name,
                           @Param("mediaType") String mediaType,
                           @Param("sizeBytes") long sizeBytes,
                           @Param("storageRef") String storageRef,
                           @Param("contentHash") String contentHash,
                           @Param("updatedAt") Instant updatedAt);
}
