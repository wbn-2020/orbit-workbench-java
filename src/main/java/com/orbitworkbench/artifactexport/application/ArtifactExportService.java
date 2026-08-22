package com.orbitworkbench.artifactexport.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.artifact.application.ArtifactContentPolicy;
import com.orbitworkbench.artifactexport.api.ArtifactExportDtos.ArtifactExportResponse;
import com.orbitworkbench.artifactexport.api.ArtifactExportDtos.CreateArtifactExportRequest;
import com.orbitworkbench.artifactexport.domain.ArtifactExportRecord;
import com.orbitworkbench.artifactexport.infrastructure.mapper.ArtifactExportMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.shared.config.StorageProperties;
import com.orbitworkbench.storage.application.LocalStorageService;
import com.orbitworkbench.storage.application.StorageCleanupAuditService;
import com.orbitworkbench.storage.domain.StoredFile;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ArtifactExportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArtifactExportService.class);
    private static final Set<String> FORMATS = Set.of("MARKDOWN", "JSON", "CSV");
    private static final int MAX_LIST_SIZE = 100;

    private final ArtifactExportMapper mapper;
    private final LocalStorageService storageService;
    private final StorageCleanupAuditService cleanupAuditService;
    private final TransactionTemplate transactionTemplate;
    private final ArtifactContentPolicy contentPolicy;
    private final long maxExportBytes;

    public ArtifactExportService(ArtifactExportMapper mapper,
                                 LocalStorageService storageService,
                                 StorageCleanupAuditService cleanupAuditService,
                                 TransactionTemplate transactionTemplate,
                                 StorageProperties storageProperties,
                                 ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.storageService = storageService;
        this.cleanupAuditService = cleanupAuditService;
        this.transactionTemplate = transactionTemplate;
        this.contentPolicy = new ArtifactContentPolicy(objectMapper);
        if (storageProperties.maxArtifactBytes() <= 0) {
            throw new IllegalStateException("orbit.storage.max-artifact-bytes 必须大于 0");
        }
        this.maxExportBytes = storageProperties.maxArtifactBytes();
    }

    public ArtifactExportResponse create(Long artifactId,
                                         CreateArtifactExportRequest request,
                                         String idempotencyKey) {
        if (artifactId == null || artifactId <= 0
                || request == null
                || request.artifactVersionId() == null
                || request.artifactVersionId() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "artifactId 和 artifactVersionId 必须为正整数");
        }
        String exportKey = normalizeIdempotencyKey(idempotencyKey);
        String format = normalizeFormat(request.format());
        String fingerprint = requestFingerprint(
                artifactId, request.artifactVersionId(), format);
        Reservation reservation = required(transactionTemplate.execute(status ->
                reserve(
                        artifactId,
                        request.artifactVersionId(),
                        exportKey,
                        format,
                        fingerprint)));
        if (!reservation.created()) {
            return get(reservation.export().getId());
        }

        Long exportId = reservation.export().getId();
        StoredFile storedFile = null;
        try {
            transactionTemplate.executeWithoutResult(status -> {
                if (mapper.markRunning(exportId, Instant.now()) != 1) {
                    throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                            "导出状态已变化，请刷新后重试");
                }
            });
            ArtifactExportRecord source = reservation.source();
            String content = storageService.readUtf8(source.getSourceContentRef());
            contentPolicy.validate(
                    source.getArtifactType(), source.getSourceContentFormat(), content);
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > maxExportBytes) {
                throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, ErrorCode.FILE_TOO_LARGE,
                        "导出内容超过大小限制");
            }
            storedFile = storageService.storeExport(
                    new ByteArrayInputStream(bytes), maxExportBytes);
            StoredFile completedFile = storedFile;
            transactionTemplate.executeWithoutResult(status -> {
                if (mapper.markSucceeded(
                        exportId,
                        completedFile.storageRef(),
                        completedFile.sizeBytes(),
                        completedFile.contentHash(),
                        Instant.now()) != 1) {
                    throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                            "导出状态已变化，请刷新后重试");
                }
            });
            return get(exportId);
        } catch (RuntimeException exception) {
            ArtifactExportRecord completed = findSucceeded(exportId);
            if (completed != null) {
                return toResponse(completed);
            }
            if (storedFile != null) {
                moveToTrashOrInventory(exportId, storedFile.storageRef(), exception);
            }
            markFailed(exportId, exception);
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ErrorCode.ARTIFACT_EXPORT_FAILED,
                    "成果导出失败，请查看导出记录");
        }
    }

    private ArtifactExportRecord findSucceeded(Long exportId) {
        try {
            ArtifactExportRecord export = transactionTemplate.execute(
                    status -> mapper.findById(exportId));
            return export != null && "SUCCEEDED".equals(export.getStatus())
                    ? export
                    : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public ArtifactExportResponse get(Long exportId) {
        ArtifactExportRecord export = required(transactionTemplate.execute(
                status -> mapper.findById(exportId)));
        return toResponse(export);
    }

    public List<ArtifactExportResponse> list(Long artifactId) {
        int count = required(transactionTemplate.execute(
                status -> mapper.countActiveArtifact(artifactId)));
        if (count != 1) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "成果不存在");
        }
        List<ArtifactExportRecord> exports = required(transactionTemplate.execute(
                status -> mapper.findByArtifactId(artifactId, MAX_LIST_SIZE)));
        return exports.stream().map(this::toResponse).toList();
    }

    public ArtifactExportDownload download(Long exportId) {
        ArtifactExportRecord export = required(transactionTemplate.execute(
                status -> mapper.findById(exportId)));
        if (!"SUCCEEDED".equals(export.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.ARTIFACT_EXPORT_NOT_READY,
                    "导出文件尚未准备完成");
        }
        ArtifactExportRecord downloadable = required(transactionTemplate.execute(
                status -> mapper.findDownloadableById(exportId)));
        if (downloadable.getStorageRef() == null || downloadable.getSizeBytes() == null) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.ARTIFACT_EXPORT_NOT_READY,
                    "导出文件元数据不完整");
        }
        return new ArtifactExportDownload(
                storageService.open(downloadable.getStorageRef()),
                downloadFileName(downloadable),
                mediaType(downloadable.getFormat()),
                downloadable.getSizeBytes());
    }

    private Reservation reserve(Long artifactId,
                                Long artifactVersionId,
                                String exportKey,
                                String format,
                                String fingerprint) {
        ArtifactExportRecord existing = mapper.findByExportKeyForUpdate(exportKey);
        if (existing != null) {
            validateExistingFingerprint(existing, fingerprint);
            return new Reservation(existing, null, false);
        }

        ArtifactExportRecord source =
                mapper.findSourceForUpdate(artifactId, artifactVersionId);
        if (source == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "成果或成果版本不存在");
        }
        validateExportFormat(source, format);

        Instant now = Instant.now();
        ArtifactExportRecord export = new ArtifactExportRecord();
        export.setArtifactId(artifactId);
        export.setArtifactVersionId(artifactVersionId);
        export.setExportKey(exportKey);
        export.setRequestFingerprint(fingerprint);
        export.setFormat(format);
        export.setStatus("PENDING");
        export.setCreatedAt(now);
        export.setUpdatedAt(now);
        if (mapper.insertPending(export) != 1) {
            existing = mapper.findByExportKeyForUpdate(exportKey);
            if (existing == null) {
                throw new ApiException(HttpStatus.CONFLICT, ErrorCode.DUPLICATE_REQUEST,
                        "导出幂等请求未能完成，请重试");
            }
            validateExistingFingerprint(existing, fingerprint);
            return new Reservation(existing, null, false);
        }
        return new Reservation(export, source, true);
    }

    private void validateExistingFingerprint(ArtifactExportRecord existing,
                                             String fingerprint) {
        if (!fingerprint.equals(existing.getRequestFingerprint())) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.DUPLICATE_REQUEST,
                    "Idempotency-Key 已用于其他导出请求");
        }
    }

    private void validateExportFormat(ArtifactExportRecord source, String requestedFormat) {
        String expectedFormat = switch (source.getArtifactType()) {
            case "CHART_SPEC" -> "JSON";
            case "DATA_EXPORT" -> "CSV";
            default -> "MARKDOWN";
        };
        if (!expectedFormat.equals(source.getSourceContentFormat())
                || !expectedFormat.equals(requestedFormat)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "请求格式与成果类型或成果版本格式不匹配");
        }
    }

    private void markFailed(Long exportId, RuntimeException cause) {
        String summary = sanitizedFailureSummary(cause, null);
        try {
            transactionTemplate.executeWithoutResult(status ->
                    mapper.markFailed(
                            exportId,
                            ErrorCode.ARTIFACT_EXPORT_FAILED.name(),
                            summary,
                            Instant.now()));
        } catch (RuntimeException persistenceException) {
            cause.addSuppressed(persistenceException);
            LOGGER.error(
                    "Artifact export failure state could not be persisted: exportId={}, type={}",
                    exportId,
                    persistenceException.getClass().getSimpleName());
        }
    }

    private void moveToTrashOrInventory(Long exportId,
                                        String storageRef,
                                        RuntimeException cause) {
        try {
            storageService.moveToTrash(storageRef);
        } catch (RuntimeException cleanupException) {
            cause.addSuppressed(cleanupException);
            cleanupAuditService.record(
                    "ARTIFACT_EXPORT", exportId, storageRef, cleanupException);
        }
    }

    private String normalizeIdempotencyKey(String value) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "Idempotency-Key 不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > 128) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "Idempotency-Key 长度不能超过 128 个字符");
        }
        return normalized;
    }

    private String normalizeFormat(String value) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "format 不能为空");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!FORMATS.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "format 必须为 MARKDOWN、JSON 或 CSV");
        }
        return normalized;
    }

    private String requestFingerprint(Long artifactId,
                                      Long artifactVersionId,
                                      String format) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((artifactId + "|" + artifactVersionId + "|" + format)
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JRE 不支持 SHA-256", exception);
        }
    }

    private String sanitizedFailureSummary(RuntimeException exception, String storageRef) {
        String summary = exception.getMessage();
        if (summary == null || summary.isBlank()) {
            summary = exception.getClass().getSimpleName();
        }
        if (storageRef != null && !storageRef.isBlank()) {
            summary = summary.replace(storageRef, "[storage-ref]");
        }
        summary = summary.replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ');
        return limit(summary, 512);
    }

    private String downloadFileName(ArtifactExportRecord export) {
        String base = export.getArtifactTitle() == null
                ? "artifact"
                : export.getArtifactTitle()
                .replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_")
                .strip();
        if (base.isBlank()) {
            base = "artifact";
        }
        base = limit(base, 100);
        String extension = switch (export.getFormat()) {
            case "JSON" -> ".json";
            case "CSV" -> ".csv";
            default -> ".md";
        };
        return base + "-v" + export.getVersionNumber() + extension;
    }

    private MediaType mediaType(String format) {
        return switch (format) {
            case "JSON" -> MediaType.APPLICATION_JSON;
            case "CSV" -> MediaType.parseMediaType("text/csv;charset=UTF-8");
            default -> MediaType.parseMediaType("text/markdown;charset=UTF-8");
        };
    }

    private String limit(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private ArtifactExportResponse toResponse(ArtifactExportRecord export) {
        return new ArtifactExportResponse(
                export.getId(),
                export.getArtifactId(),
                export.getArtifactVersionId(),
                export.getVersionNumber(),
                export.getArtifactTitle(),
                export.getArtifactType(),
                export.getFormat(),
                export.getStatus(),
                export.getSizeBytes(),
                export.getContentHash(),
                export.getErrorCode(),
                export.getErrorSummary(),
                export.getWorkspaceId(),
                export.getTaskId(),
                export.getSourceRunId(),
                export.getDatasetId(),
                export.getSheetId(),
                export.getCreatedAt(),
                export.getFinishedAt());
    }

    private <T> T required(T value) {
        if (value == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "导出记录不存在");
        }
        return value;
    }

    private record Reservation(
            ArtifactExportRecord export,
            ArtifactExportRecord source,
            boolean created
    ) {
    }
}
