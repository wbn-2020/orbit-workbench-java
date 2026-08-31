package com.orbitworkbench.resume.infrastructure.mapper;

import com.orbitworkbench.resume.domain.ResumeDocumentRecord;
import com.orbitworkbench.resume.domain.ResumeVersionRecord;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * resume_version 没有 user_id 列（V28 已锁定不可改），归属一律先由
 * findDocumentByUser 拿到 resumeId 再按 resumeId 取版本，服务层不得跳过这一步。
 */
public interface ResumeMapper {

    void insertDocument(ResumeDocumentRecord record);

    ResumeDocumentRecord findDocumentByUser(@Param("userId") Long userId);

    ResumeDocumentRecord findDocumentByUserForUpdate(@Param("userId") Long userId);

    int updateDocumentMeta(@Param("documentId") Long documentId,
                           @Param("userId") Long userId,
                           @Param("title") String title,
                           @Param("activeVersionId") Long activeVersionId,
                           @Param("updatedAt") Instant updatedAt);

    void insertVersion(ResumeVersionRecord record);

    ResumeVersionRecord findVersion(@Param("versionId") Long versionId,
                                    @Param("resumeId") Long resumeId);

    List<ResumeVersionRecord> listVersions(@Param("resumeId") Long resumeId);

    ResumeVersionRecord findDraft(@Param("resumeId") Long resumeId);

    ResumeVersionRecord findDraftForUpdate(@Param("resumeId") Long resumeId);

    int nextVersionNumber(@Param("resumeId") Long resumeId);

    int updateDraftSections(@Param("versionId") Long versionId,
                            @Param("resumeId") Long resumeId,
                            @Param("sectionsJson") String sectionsJson,
                            @Param("changeSummary") String changeSummary,
                            @Param("expectedUpdatedAt") Instant expectedUpdatedAt,
                            @Param("updatedAt") Instant updatedAt);

    int finalizeDraft(@Param("versionId") Long versionId,
                      @Param("resumeId") Long resumeId,
                      @Param("sourceSnapshotJson") String sourceSnapshotJson,
                      @Param("changeSummary") String changeSummary,
                      @Param("updatedAt") Instant updatedAt);

    int updatePdfPointer(@Param("versionId") Long versionId,
                         @Param("resumeId") Long resumeId,
                         @Param("storageRef") String storageRef,
                         @Param("generatedAt") Instant generatedAt);
}
