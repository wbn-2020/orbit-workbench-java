package com.orbitworkbench.resume.application;

import com.orbitworkbench.resume.api.ResumeDtos.PreflightResponse;
import com.orbitworkbench.resume.application.ResumePdfRenderer.Rendered;
import com.orbitworkbench.resume.application.ResumeSections.Model;
import com.orbitworkbench.resume.domain.ResumeDocumentRecord;
import com.orbitworkbench.resume.domain.ResumeVersionRecord;
import com.orbitworkbench.resume.infrastructure.mapper.ResumeMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.shared.config.StorageProperties;
import com.orbitworkbench.storage.application.LocalStorageService;
import com.orbitworkbench.storage.domain.StoredFile;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 导出编排（13 §7–§9）：先校验再落文件，文件写入失败不留指针；
 * 旧文件进回收站而不是直接删除（D-02 未决，必须可撤销）。
 */
@Service
public class ResumeExportService {

    private static final Logger log = LoggerFactory.getLogger(ResumeExportService.class);

    public record Exported(int sizeBytes, String fontName) {
    }

    public record Downloadable(byte[] content, String fileName) {
    }

    private final ResumeMapper resumeMapper;
    private final ResumeExportRecorder recorder;
    private final ResumeSectionsJson json;
    private final ResumeExportPreflight preflight;
    private final ResumePdfRenderer renderer;
    private final LocalStorageService storage;
    private final StorageProperties properties;

    public ResumeExportService(ResumeMapper resumeMapper, ResumeExportRecorder recorder,
                               ResumeSectionsJson json, ResumeExportPreflight preflight,
                               ResumePdfRenderer renderer, LocalStorageService storage,
                               StorageProperties properties) {
        this.resumeMapper = resumeMapper;
        this.recorder = recorder;
        this.json = json;
        this.preflight = preflight;
        this.renderer = renderer;
        this.storage = storage;
        this.properties = properties;
    }

    public PreflightResponse preflight(Long userId, Long versionId) {
        ResumeVersionRecord version = requireVersion(userId, versionId);
        return preflight.evaluate(json.read(version.getSectionsJson()));
    }

    @Transactional
    public Exported export(Long userId, Long versionId) {
        ResumeDocumentRecord document = ResumeSupport.requireDocument(resumeMapper, userId);
        ResumeVersionRecord version = ResumeSupport.requireVersion(resumeMapper, document, versionId);
        Model stored = json.read(version.getSectionsJson());
        Model renderable = new Model(stored.schemaVersion(),
                ResumeExportPreflight.withSanitizedText(stored));
        PreflightResponse check = preflight.evaluate(renderable);
        if (!check.ready()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "导出前校验未通过：" + String.join("；", check.blockers()));
        }

        Rendered rendered;
        try {
            rendered = renderer.render(document.getTitle(), renderable);
        } catch (ApiException exception) {
            recorder.recordFailure(versionId, userId, null, exception.getMessage(), Instant.now());
            throw exception;
        }

        StoredFile storedFile;
        try {
            storedFile = storage.storeExport(new ByteArrayInputStream(rendered.content()),
                    properties.maxArtifactBytes());
        } catch (ApiException exception) {
            recorder.recordFailure(versionId, userId, rendered.fontName(), exception.getMessage(),
                    Instant.now());
            throw exception;
        }

        String previousRef = version.getPdfStorageRef();
        Instant now = Instant.now();
        try {
            recorder.recordSuccess(versionId, userId, storedFile.storageRef(), storedFile.contentHash(),
                    storedFile.sizeBytes(), rendered.fontName(), now);
            if (resumeMapper.updatePdfPointer(versionId, document.getId(), storedFile.storageRef(), now) != 1) {
                throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                        "简历版本已变化，请刷新后重试");
            }
        } catch (RuntimeException exception) {
            trashQuietly(storedFile.storageRef());
            throw exception;
        }
        if (previousRef != null) {
            trashQuietly(previousRef);
        }
        log.info("简历 PDF 导出成功 userId={} versionId={} bytes={}", userId, versionId,
                storedFile.sizeBytes());
        return new Exported((int) storedFile.sizeBytes(), rendered.fontName());
    }

    public Downloadable download(Long userId, Long versionId) {
        ResumeDocumentRecord document = ResumeSupport.requireDocument(resumeMapper, userId);
        ResumeVersionRecord version = ResumeSupport.requireVersion(resumeMapper, document, versionId);
        if (version.getPdfStorageRef() == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.FILE_NOT_FOUND,
                    "该版本还没有导出文件");
        }
        byte[] content;
        try (var input = storage.open(version.getPdfStorageRef())) {
            content = input.readAllBytes();
        } catch (ApiException exception) {
            throw exception;
        } catch (java.io.IOException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.FILE_NOT_FOUND,
                    "导出文件已不可用，请重新生成");
        }
        return new Downloadable(content, ResumeSupport.fileName(document.getTitle(),
                version.getVersionNumber()));
    }

    private ResumeVersionRecord requireVersion(Long userId, Long versionId) {
        return ResumeSupport.requireVersion(resumeMapper,
                ResumeSupport.requireDocument(resumeMapper, userId), versionId);
    }

    private void trashQuietly(String storageRef) {
        try {
            storage.moveToTrash(storageRef);
        } catch (RuntimeException exception) {
            log.warn("导出文件回收站移动失败，文件仍保留在原位置 storageRef={}", storageRef);
        }
    }
}
