package com.orbitworkbench.resume.application;

import com.orbitworkbench.resume.domain.ResumeExportRecord;
import com.orbitworkbench.resume.domain.ResumeExportStatus;
import com.orbitworkbench.resume.infrastructure.mapper.ResumeExportMapper;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 导出留痕（13 §4.2）。成功行随导出事务一起提交或回滚；
 * 失败行必须活过回滚，所以用 REQUIRES_NEW 独立事务写，与 ai_call_audit 同一口径。
 */
@Component
public class ResumeExportRecorder {

    private static final int REASON_MAX_LENGTH = 512;

    private final ResumeExportMapper exportMapper;

    public ResumeExportRecorder(ResumeExportMapper exportMapper) {
        this.exportMapper = exportMapper;
    }

    public void recordSuccess(Long versionId, Long userId, String storageRef, String contentHash,
                              long sizeBytes, String fontName, Instant now) {
        exportMapper.insert(build(versionId, userId, storageRef, contentHash, sizeBytes, fontName,
                ResumeExportStatus.SUCCEEDED, null, now));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long versionId, Long userId, String fontName, String reason, Instant now) {
        String summary = reason == null || reason.isBlank() ? "导出失败" : reason.trim();
        exportMapper.insert(build(versionId, userId, null, null, null, fontName,
                ResumeExportStatus.FAILED,
                summary.length() > REASON_MAX_LENGTH ? summary.substring(0, REASON_MAX_LENGTH) : summary,
                now));
    }

    private ResumeExportRecord build(Long versionId, Long userId, String storageRef, String contentHash,
                                     Long sizeBytes, String fontName, ResumeExportStatus status,
                                     String reason, Instant now) {
        ResumeExportRecord record = new ResumeExportRecord();
        record.setResumeVersionId(versionId);
        record.setUserId(userId);
        record.setStorageRef(storageRef);
        record.setContentHash(contentHash);
        record.setSizeBytes(sizeBytes);
        record.setFontName(fontName);
        record.setStatus(status);
        record.setFailureReason(reason);
        record.setCreatedAt(now);
        return record;
    }
}
