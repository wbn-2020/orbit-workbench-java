package com.orbitworkbench.artifact.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.artifact.api.ArtifactDtos.ArtifactDetailResponse;
import com.orbitworkbench.artifact.api.ArtifactDtos.ArtifactSummaryResponse;
import com.orbitworkbench.artifact.api.ArtifactDtos.ArtifactVersionResponse;
import com.orbitworkbench.artifact.api.ArtifactDtos.ArtifactVersionSummaryResponse;
import com.orbitworkbench.artifact.api.ArtifactDtos.UpdateArtifactRequest;
import com.orbitworkbench.artifact.domain.ArtifactRecord;
import com.orbitworkbench.artifact.domain.ArtifactVersionRecord;
import com.orbitworkbench.artifact.infrastructure.mapper.ArtifactMapper;
import com.orbitworkbench.artifact.infrastructure.mapper.ArtifactVersionMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.shared.api.PageResult;
import com.orbitworkbench.shared.config.StorageProperties;
import com.orbitworkbench.storage.application.LocalStorageService;
import com.orbitworkbench.storage.application.StorageCleanupAuditService;
import com.orbitworkbench.storage.domain.StoredFile;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ArtifactService {

    private static final String READY = "READY";
    private static final String DEFAULT_CONTENT_FORMAT = "MARKDOWN";
    private static final Set<String> CONTENT_FORMATS = Set.of("MARKDOWN", "JSON", "CSV");
    private static final Set<String> ARTIFACT_TYPES = Set.of(
            "LEARNING_NOTE", "QUIZ", "SUMMARY",
            "ANALYSIS_REPORT", "CHART_SPEC", "DATA_EXPORT",
            "CONTENT_DRAFT", "CONTENT_REVIEW");

    private final ArtifactMapper artifactMapper;
    private final ArtifactVersionMapper artifactVersionMapper;
    private final LocalStorageService storageService;
    private final StorageCleanupAuditService cleanupAuditService;
    private final ArtifactContentPolicy contentPolicy;
    private final long maxArtifactBytes;

    public ArtifactService(ArtifactMapper artifactMapper,
                            ArtifactVersionMapper artifactVersionMapper,
                            LocalStorageService storageService,
                            StorageProperties storageProperties) {
        this(
                artifactMapper,
                artifactVersionMapper,
                storageService,
                storageProperties,
                new ObjectMapper(),
                null);
    }

    @Autowired
    public ArtifactService(ArtifactMapper artifactMapper,
                           ArtifactVersionMapper artifactVersionMapper,
                           LocalStorageService storageService,
                           StorageProperties storageProperties,
                           ObjectMapper objectMapper,
                           StorageCleanupAuditService cleanupAuditService) {
        this.artifactMapper = artifactMapper;
        this.artifactVersionMapper = artifactVersionMapper;
        this.storageService = storageService;
        this.cleanupAuditService = cleanupAuditService;
        this.contentPolicy = new ArtifactContentPolicy(objectMapper);
        if (storageProperties.maxArtifactBytes() <= 0) {
            throw new IllegalStateException("orbit.storage.max-artifact-bytes 必须大于 0");
        }
        this.maxArtifactBytes = storageProperties.maxArtifactBytes();
    }

    @Transactional(readOnly = true)
    public PageResult<ArtifactSummaryResponse> findPage(Long workspaceId,
                                                        Long taskId,
                                                        int page,
                                                        int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        long offset = ((long) normalizedPage - 1) * normalizedSize;
        List<ArtifactSummaryResponse> items = artifactMapper
                .findPage(workspaceId, taskId, offset, normalizedSize)
                .stream()
                .map(this::toSummary)
                .toList();
        long total = artifactMapper.countPage(workspaceId, taskId);
        return new PageResult<>(items, normalizedPage, normalizedSize, total);
    }

    @Transactional(readOnly = true)
    public ArtifactDetailResponse get(Long id) {
        ArtifactRecord artifact = requireArtifact(id);
        return toDetail(artifact);
    }

    @Transactional(readOnly = true)
    public List<ArtifactVersionSummaryResponse> versions(Long id) {
        requireArtifact(id);
        return artifactVersionMapper.findByArtifactId(id)
                .stream()
                .map(this::toVersionSummaryResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ArtifactVersionResponse version(Long id, Long versionId) {
        ArtifactVersionRecord version = artifactVersionMapper.findByArtifactIdAndId(id, versionId);
        if (version == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "成果版本不存在");
        }
        return toVersionResponse(version);
    }

    @Transactional(readOnly = true)
    public Long currentVersionId(Long artifactId) {
        ArtifactVersionRecord version = artifactVersionMapper.findLatestByArtifactId(artifactId);
        if (version == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "成果版本不存在");
        }
        return version.getId();
    }

    @Transactional
    public ArtifactDetailResponse createInitialArtifact(CreateInitialArtifactCommand command) {
        validateInitialCommand(command);
        String artifactType = normalizeArtifactType(command.artifactType());
        String contentFormat = normalizeContentFormat(command.contentFormat());
        validateTypeFormat(artifactType, contentFormat);
        contentPolicy.validate(artifactType, contentFormat, command.content());
        StoredFile storedFile = storeContent(command.content());
        boolean rollbackCleanupRegistered = registerRollbackCleanup(storedFile);
        try {
            Instant now = Instant.now();
            ArtifactRecord artifact = new ArtifactRecord();
            artifact.setWorkspaceId(command.workspaceId());
            artifact.setTaskId(command.taskId());
            artifact.setSourceRunId(command.sourceRunId());
            artifact.setArtifactType(artifactType);
            artifact.setTitle(normalizeTitle(command.title()));
            artifact.setStatus(READY);
            artifact.setCreatedAt(now);
            artifact.setUpdatedAt(now);
            artifactMapper.insert(artifact);

            ArtifactVersionRecord version = new ArtifactVersionRecord();
            version.setArtifactId(artifact.getId());
            version.setVersionNumber(1);
            version.setContentRef(storedFile.storageRef());
            version.setContentFormat(contentFormat);
            version.setSourceRunId(command.sourceRunId());
            version.setChangeSummary(trimToNull(command.changeSummary()));
            version.setCreatedAt(now);
            artifactVersionMapper.insert(version);

            if (artifactMapper.updateCurrentVersion(
                    artifact.getId(), artifact.getTitle(), version.getId(), now) != 1) {
                throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                        "成果当前版本更新失败");
            }
            return get(artifact.getId());
        } catch (RuntimeException exception) {
            if (!rollbackCleanupRegistered) {
                moveStoredFileToTrash(storedFile, exception);
            }
            throw exception;
        }
    }

    @Transactional
    public ArtifactDetailResponse update(Long id, UpdateArtifactRequest request) {
        if (request == null || request.expectedVersion() == null
                || request.title() == null || request.title().isBlank()
                || request.content() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "成果标题和内容不能为空");
        }

        ArtifactRecord artifact = requireArtifactForUpdate(id);
        int currentVersion = artifact.getCurrentVersionNumber() == null
                ? 0
                : artifact.getCurrentVersionNumber();
        if (currentVersion < 1) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "成果缺少当前版本");
        }
        if (request.expectedVersion() != currentVersion) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "成果版本已变化，请刷新后重试");
        }

        String contentFormat = artifact.getCurrentContentFormat() == null
                ? DEFAULT_CONTENT_FORMAT
                : normalizeContentFormat(artifact.getCurrentContentFormat());
        if (artifact.getArtifactType() != null) {
            validateTypeFormat(artifact.getArtifactType(), contentFormat);
        }
        contentPolicy.validate(artifact.getArtifactType(), contentFormat, request.content());
        StoredFile storedFile = storeContent(request.content());
        boolean rollbackCleanupRegistered = registerRollbackCleanup(storedFile);
        try {
            Instant now = Instant.now();
            ArtifactVersionRecord version = new ArtifactVersionRecord();
            version.setArtifactId(id);
            version.setVersionNumber(currentVersion + 1);
            version.setContentRef(storedFile.storageRef());
            version.setContentFormat(contentFormat);
            version.setChangeSummary("手动编辑");
            version.setCreatedAt(now);
            artifactVersionMapper.insert(version);

            String title = normalizeTitle(request.title());
            if (artifactMapper.updateCurrentVersion(id, title, version.getId(), now) != 1) {
                throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                        "成果状态已变化，请刷新后重试");
            }
            return get(id);
        } catch (RuntimeException exception) {
            if (!rollbackCleanupRegistered) {
                moveStoredFileToTrash(storedFile, exception);
            }
            throw exception;
        }
    }

    private ArtifactRecord requireArtifact(Long id) {
        ArtifactRecord artifact = artifactMapper.findById(id);
        if (artifact == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "成果不存在");
        }
        return artifact;
    }

    private ArtifactRecord requireArtifactForUpdate(Long id) {
        ArtifactRecord artifact = artifactMapper.findByIdForUpdate(id);
        if (artifact == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "成果不存在");
        }
        return artifact;
    }

    private StoredFile storeContent(String content) {
        byte[] utf8Content = content.getBytes(StandardCharsets.UTF_8);
        if (utf8Content.length > maxArtifactBytes) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, ErrorCode.FILE_TOO_LARGE,
                    "成果内容超过大小限制");
        }
        return storageService.storeArtifact(new ByteArrayInputStream(utf8Content));
    }

    private void validateInitialCommand(CreateInitialArtifactCommand command) {
        if (command == null || command.workspaceId() == null || command.taskId() == null
                || command.sourceRunId() == null
                || command.artifactType() == null || command.title() == null
                || command.content() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "创建成果所需字段不能为空");
        }
        normalizeText(command.artifactType(), "artifactType", 64);
        normalizeTitle(command.title());
        if (artifactMapper.countSourceContext(
                command.workspaceId(), command.taskId(), command.sourceRunId()) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "成果来源运行与任务或工作空间不一致");
        }
    }

    private String normalizeContentFormat(String contentFormat) {
        String normalized = contentFormat == null || contentFormat.isBlank()
                ? DEFAULT_CONTENT_FORMAT
                : contentFormat.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > 32) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "contentFormat 长度不能超过 32");
        }
        if (!CONTENT_FORMATS.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "contentFormat 必须为 MARKDOWN、JSON 或 CSV");
        }
        return normalized;
    }

    private String normalizeArtifactType(String artifactType) {
        String normalized = normalizeText(artifactType, "artifactType", 64)
                .toUpperCase(Locale.ROOT);
        if (!ARTIFACT_TYPES.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "artifactType 不受支持");
        }
        return normalized;
    }

    private void validateTypeFormat(String artifactType, String contentFormat) {
        String expectedFormat = switch (artifactType) {
            case "CHART_SPEC" -> "JSON";
            case "DATA_EXPORT" -> "CSV";
            default -> "MARKDOWN";
        };
        if (!expectedFormat.equals(contentFormat)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "成果类型与内容格式不匹配");
        }
    }

    private String normalizeTitle(String title) {
        return normalizeText(title, "title", 255);
    }

    private String normalizeText(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    fieldName + " 不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    fieldName + " 长度不能超过 " + maxLength);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void moveStoredFileToTrash(StoredFile storedFile, RuntimeException cause) {
        try {
            storageService.moveToTrash(storedFile.storageRef());
        } catch (RuntimeException trashException) {
            cause.addSuppressed(trashException);
            recordCleanupFailure(storedFile.storageRef(), trashException);
        }
    }

    private boolean registerRollbackCleanup(StoredFile storedFile) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return false;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    try {
                        storageService.moveToTrash(storedFile.storageRef());
                    } catch (RuntimeException cleanupException) {
                        recordCleanupFailure(storedFile.storageRef(), cleanupException);
                    }
                }
            }
        });
        return true;
    }

    private void recordCleanupFailure(String storageRef,
                                      RuntimeException exception) {
        if (cleanupAuditService != null) {
            cleanupAuditService.record(
                    "ARTIFACT",
                    null,
                    storageRef,
                    exception);
        }
    }

    private ArtifactSummaryResponse toSummary(ArtifactRecord artifact) {
        return new ArtifactSummaryResponse(
                artifact.getId(),
                artifact.getTaskId(),
                artifact.getTaskTitle(),
                artifact.getTitle(),
                artifact.getArtifactType(),
                artifact.getCurrentVersionNumber(),
                artifact.getUpdatedAt(),
                artifact.getCreatedAt()
        );
    }

    private ArtifactDetailResponse toDetail(ArtifactRecord artifact) {
        if (artifact.getCurrentVersionId() == null || artifact.getCurrentContentRef() == null) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "成果缺少当前版本");
        }
        return new ArtifactDetailResponse(
                artifact.getId(),
                artifact.getTaskId(),
                artifact.getTaskTitle(),
                artifact.getTitle(),
                artifact.getArtifactType(),
                artifact.getCurrentVersionNumber(),
                artifact.getCurrentContentFormat(),
                storageService.readUtf8(artifact.getCurrentContentRef()),
                artifact.getUpdatedAt(),
                artifact.getCreatedAt()
        );
    }

    private ArtifactVersionSummaryResponse toVersionSummaryResponse(ArtifactVersionRecord version) {
        return new ArtifactVersionSummaryResponse(
                version.getId(),
                version.getArtifactId(),
                version.getVersionNumber(),
                version.getContentFormat(),
                version.getSourceRunId(),
                version.getChangeSummary(),
                version.getCreatedAt()
        );
    }

    private ArtifactVersionResponse toVersionResponse(ArtifactVersionRecord version) {
        return new ArtifactVersionResponse(
                version.getId(),
                version.getArtifactId(),
                version.getVersionNumber(),
                storageService.readUtf8(version.getContentRef()),
                version.getContentFormat(),
                version.getSourceRunId(),
                version.getChangeSummary(),
                version.getCreatedAt()
        );
    }
}
