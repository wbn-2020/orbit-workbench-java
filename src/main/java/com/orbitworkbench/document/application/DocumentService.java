package com.orbitworkbench.document.application;

import com.orbitworkbench.document.api.DocumentDtos.DocumentContent;
import com.orbitworkbench.document.api.DocumentDtos.DocumentDetailResponse;
import com.orbitworkbench.document.api.DocumentDtos.DocumentSummaryResponse;
import com.orbitworkbench.document.api.DocumentDtos.DocumentText;
import com.orbitworkbench.document.api.DocumentDtos.StorageCleanupFailureResponse;
import com.orbitworkbench.document.domain.DocumentRecord;
import com.orbitworkbench.document.domain.StorageCleanupFailureRecord;
import com.orbitworkbench.document.infrastructure.mapper.DocumentMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.shared.api.PageResult;
import com.orbitworkbench.shared.config.StorageProperties;
import com.orbitworkbench.storage.application.LocalStorageService;
import com.orbitworkbench.storage.domain.StoredFile;
import com.orbitworkbench.storage.domain.TrashEntry;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService {

    private static final String READY = "READY";
    private static final String CLEANUP_PENDING = "PENDING";
    private static final String CLEANUP_RESOLVED = "RESOLVED";
    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentService.class);
    private static final int CONTROL_CHARACTER_PERCENT_DIVISOR = 100;
    private static final int CONTROL_CHARACTER_MINIMUM_ALLOWANCE = 4;

    private final DocumentMapper documentMapper;
    private final LocalStorageService storageService;
    private final long maxDocumentBytes;
    private final TransactionTemplate cleanupFailureTransaction;

    @Autowired
    public DocumentService(DocumentMapper documentMapper,
                           LocalStorageService storageService,
                           StorageProperties storageProperties,
                           PlatformTransactionManager transactionManager) {
        this(documentMapper, storageService, storageProperties,
                cleanupFailureTransaction(transactionManager));
    }

    DocumentService(DocumentMapper documentMapper,
                    LocalStorageService storageService,
                    StorageProperties storageProperties) {
        this(documentMapper, storageService, storageProperties, (TransactionTemplate) null);
    }

    private DocumentService(DocumentMapper documentMapper,
                            LocalStorageService storageService,
                            StorageProperties storageProperties,
                            TransactionTemplate cleanupFailureTransaction) {
        this.documentMapper = documentMapper;
        this.storageService = storageService;
        this.cleanupFailureTransaction = cleanupFailureTransaction;
        if (storageProperties.maxDocumentBytes() <= 0) {
            throw new IllegalStateException("orbit.storage.max-document-bytes 必须大于 0");
        }
        this.maxDocumentBytes = storageProperties.maxDocumentBytes();
    }

    @Transactional(readOnly = true)
    public PageResult<DocumentSummaryResponse> findPage(Long workspaceId, int page, int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        long offset = ((long) normalizedPage - 1) * normalizedSize;
        List<DocumentSummaryResponse> items = documentMapper
                .findPage(workspaceId, offset, normalizedSize)
                .stream()
                .map(this::toSummary)
                .toList();
        long total = documentMapper.countPage(workspaceId);
        return new PageResult<>(items, normalizedPage, normalizedSize, total);
    }

    @Transactional(readOnly = true)
    public DocumentDetailResponse get(Long id) {
        return toDetail(requireDocument(id));
    }

    @Transactional
    public DocumentSummaryResponse upload(Long workspaceId, MultipartFile file) {
        validateUpload(workspaceId, file);
        String originalName = normalizedOriginalName(file.getOriginalFilename());
        String mediaType = mediaTypeFor(originalName);

        StoredFile storedFile;
        try (InputStream input = file.getInputStream()) {
            storedFile = storageService.storeDocument(input, maxDocumentBytes);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.FILE_NOT_FOUND,
                    "无法读取上传文件");
        }

        boolean rollbackCleanupRegistered = registerRollbackCleanup(storedFile);
        try {
            validateStoredText(storedFile);
            if (documentMapper.lockActiveWorkspace(workspaceId) == null) {
                throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                        "工作空间不存在或不可用");
            }
            if (documentMapper.findByWorkspaceAndHash(workspaceId, storedFile.contentHash()) != null) {
                throw duplicateDocument();
            }

            Instant now = Instant.now();
            DocumentRecord document = new DocumentRecord();
            document.setWorkspaceId(workspaceId);
            document.setName(originalName);
            document.setMediaType(mediaType);
            document.setSizeBytes(storedFile.sizeBytes());
            document.setStorageRef(storedFile.storageRef());
            document.setContentHash(storedFile.contentHash());
            document.setParseStatus(READY);
            document.setCreatedAt(now);
            document.setUpdatedAt(now);
            documentMapper.insert(document);
            return toSummary(document);
        } catch (DuplicateKeyException exception) {
            ApiException duplicate = duplicateDocument();
            if (!rollbackCleanupRegistered) {
                moveStoredFileToTrash(storedFile, duplicate);
            }
            throw duplicate;
        } catch (RuntimeException exception) {
            if (!rollbackCleanupRegistered) {
                moveStoredFileToTrash(storedFile, exception);
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public DocumentContent readContent(Long id) {
        DocumentRecord document = requireDocument(id);
        requireTextDocument(document);
        return new DocumentContent(document.getMediaType(),
                storageService.readUtf8(document.getStorageRef()));
    }

    @Transactional(readOnly = true)
    public List<DocumentText> readTextsByIds(List<Long> documentIds) {
        List<Long> ids = normalizeDocumentIds(documentIds);
        if (ids.isEmpty()) {
            return List.of();
        }

        List<DocumentRecord> documents = documentMapper.findByIds(ids);
        if (documents.size() != ids.size()) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "存在已删除或不存在的资料");
        }

        Map<Long, DocumentRecord> byId = new LinkedHashMap<>();
        for (DocumentRecord document : documents) {
            byId.put(document.getId(), document);
        }

        return ids.stream()
                .map(id -> {
                    DocumentRecord document = byId.get(id);
                    requireTextDocument(document);
                    return new DocumentText(
                            document.getId(),
                            document.getName(),
                            storageService.readUtf8(document.getStorageRef())
                    );
                })
                .toList();
    }

    private void requireTextDocument(DocumentRecord document) {
        if (!"text/plain".equals(document.getMediaType())
                && !"text/markdown".equals(document.getMediaType())) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                    "该文件不是可读取的文本资料");
        }
    }

    @Transactional
    public void delete(Long id) {
        DocumentRecord document = documentMapper.findByIdForUpdate(id);
        if (document == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "资料不存在");
        }
        if (documentMapper.countTaskReferences(id) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "资料仍被任务引用，不能删除");
        }

        TrashEntry trashEntry = storageService.moveToTrash(document.getStorageRef());
        boolean rollbackRestoreRegistered = false;
        try {
            rollbackRestoreRegistered = registerRollbackRestore(trashEntry);
            if (documentMapper.deleteById(id) != 1) {
                throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                        "资料状态已变化，请刷新后重试");
            }
        } catch (RuntimeException exception) {
            if (!rollbackRestoreRegistered) {
                restoreAfterFailure(trashEntry, exception);
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public PageResult<StorageCleanupFailureResponse> findCleanupFailures(
            String status,
            int page,
            int size) {
        String normalizedStatus = normalizeCleanupStatus(status);
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        long offset = ((long) normalizedPage - 1) * normalizedSize;
        List<StorageCleanupFailureResponse> items = documentMapper
                .findCleanupFailurePage(normalizedStatus, offset, normalizedSize)
                .stream()
                .map(this::toCleanupFailureResponse)
                .toList();
        return new PageResult<>(items, normalizedPage, normalizedSize,
                documentMapper.countCleanupFailures(normalizedStatus));
    }

    @Transactional
    public StorageCleanupFailureResponse retryCleanupFailure(Long id) {
        StorageCleanupFailureRecord failure = documentMapper.findCleanupFailureByIdForUpdate(id);
        if (failure == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "存储清理失败记录不存在");
        }
        if (CLEANUP_RESOLVED.equals(failure.getStatus())) {
            return toCleanupFailureResponse(failure);
        }

        Instant retriedAt = Instant.now();
        try {
            storageService.moveToTrash(failure.getStorageRef());
        } catch (ApiException exception) {
            if (exception.getErrorCode() != ErrorCode.FILE_NOT_FOUND) {
                return recordRetryFailure(failure, exception, retriedAt);
            }
        } catch (RuntimeException exception) {
            return recordRetryFailure(failure, exception, retriedAt);
        }

        if (documentMapper.resolveCleanupFailure(id, retriedAt) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "存储清理记录状态已变化，请刷新后重试");
        }
        failure.setStatus(CLEANUP_RESOLVED);
        failure.setResolvedAt(retriedAt);
        failure.setLastRetriedAt(retriedAt);
        failure.setRetryCount(failure.getRetryCount() + 1);
        failure.setUpdatedAt(retriedAt);
        return toCleanupFailureResponse(failure);
    }

    private DocumentRecord requireDocument(Long id) {
        DocumentRecord document = documentMapper.findById(id);
        if (document == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "资料不存在");
        }
        return document;
    }

    private void validateUpload(Long workspaceId, MultipartFile file) {
        if (workspaceId == null || documentMapper.countActiveWorkspace(workspaceId) != 1) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "工作空间不存在或不可用");
        }
        if (file == null || file.getOriginalFilename() == null
                || file.getOriginalFilename().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "上传文件不能为空");
        }
        if (file.getSize() > maxDocumentBytes) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, ErrorCode.FILE_TOO_LARGE,
                    "上传文件超过大小限制");
        }
    }

    private String normalizedOriginalName(String originalName) {
        if (originalName == null || originalName.isBlank() || originalName.indexOf('\0') >= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "文件名不能为空");
        }
        String normalized = originalName.replace('\\', '/');
        int separatorIndex = normalized.lastIndexOf('/');
        String fileName = normalized.substring(separatorIndex + 1).trim();
        if (fileName.isEmpty() || fileName.length() > 255) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "文件名长度必须为 1-255");
        }
        return fileName;
    }

    private String mediaTypeFor(String fileName) {
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".txt")) {
            return "text/plain";
        }
        if (lowerName.endsWith(".md")) {
            return "text/markdown";
        }
        throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                "MVP 仅支持 .txt 和 .md 文件");
    }

    private List<Long> normalizeDocumentIds(List<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return List.of();
        }
        for (Long id : documentIds) {
            if (id == null || id <= 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                        "documentIds 必须为正整数");
            }
        }
        return new LinkedHashSet<>(documentIds).stream().toList();
    }

    private void validateStoredText(StoredFile storedFile) {
        String content = storageService.readUtf8(storedFile.storageRef());
        long codePointCount = content.codePoints().count();
        long controlCharacterCount = content.codePoints()
                .filter(DocumentService::isDisallowedControlCharacter)
                .count();
        long allowedControlCharacters = Math.max(
                CONTROL_CHARACTER_MINIMUM_ALLOWANCE,
                codePointCount / CONTROL_CHARACTER_PERCENT_DIVISOR);
        if (content.indexOf('\0') >= 0 || controlCharacterCount > allowedControlCharacters) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                    "文件正文包含过多控制字符");
        }
    }

    private boolean registerRollbackRestore(TrashEntry trashEntry) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return false;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    storageService.restore(trashEntry);
                }
            }
        });
        return true;
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

    private void restoreAfterFailure(TrashEntry trashEntry, RuntimeException cause) {
        try {
            storageService.restore(trashEntry);
        } catch (RuntimeException restoreException) {
            cause.addSuppressed(restoreException);
        }
    }

    private static boolean isDisallowedControlCharacter(int codePoint) {
        return Character.isISOControl(codePoint)
                && codePoint != '\t'
                && codePoint != '\n'
                && codePoint != '\r';
    }

    private void moveStoredFileToTrash(StoredFile storedFile, RuntimeException cause) {
        try {
            storageService.moveToTrash(storedFile.storageRef());
        } catch (RuntimeException trashException) {
            cause.addSuppressed(trashException);
            recordCleanupFailure(storedFile.storageRef(), trashException);
        }
    }

    private StorageCleanupFailureResponse recordRetryFailure(
            StorageCleanupFailureRecord failure,
            RuntimeException exception,
            Instant retriedAt) {
        String failureType = exception.getClass().getSimpleName();
        String failureSummary = sanitizedFailureSummary(exception, failure.getStorageRef());
        if (documentMapper.recordCleanupRetryFailure(
                failure.getId(), failureType, failureSummary, retriedAt) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "存储清理记录状态已变化，请刷新后重试");
        }
        LOGGER.warn(
                "Document storage cleanup retry failed: refFingerprint={}, failureType={}, reason={}",
                failure.getStorageRefFingerprint(), failureType, failureSummary);
        failure.setFailureType(failureType);
        failure.setFailureSummary(failureSummary);
        failure.setLastRetriedAt(retriedAt);
        failure.setRetryCount(failure.getRetryCount() + 1);
        failure.setUpdatedAt(retriedAt);
        return toCleanupFailureResponse(failure);
    }

    private void recordCleanupFailure(String storageRef, RuntimeException exception) {
        Instant now = Instant.now();
        StorageCleanupFailureRecord failure = new StorageCleanupFailureRecord();
        failure.setResourceType("DOCUMENT_UPLOAD");
        failure.setStorageRef(storageRef);
        failure.setStorageRefFingerprint(storageRefFingerprint(storageRef));
        failure.setOperation("MOVE_TO_TRASH");
        failure.setStatus(CLEANUP_PENDING);
        failure.setFailureType(exception.getClass().getSimpleName());
        failure.setFailureSummary(sanitizedFailureSummary(exception, storageRef));
        failure.setCreatedAt(now);
        failure.setUpdatedAt(now);

        LOGGER.error(
                "Document storage cleanup failed: refFingerprint={}, failureType={}, reason={}",
                failure.getStorageRefFingerprint(),
                failure.getFailureType(),
                failure.getFailureSummary());
        try {
            if (cleanupFailureTransaction == null) {
                documentMapper.insertCleanupFailure(failure);
            } else {
                cleanupFailureTransaction.executeWithoutResult(
                        status -> documentMapper.insertCleanupFailure(failure));
            }
        } catch (RuntimeException persistenceException) {
            LOGGER.error(
                    "Document storage cleanup failure could not be inventoried: "
                            + "refFingerprint={}, persistenceFailureType={}",
                    failure.getStorageRefFingerprint(),
                    persistenceException.getClass().getSimpleName());
            exception.addSuppressed(persistenceException);
        }
    }

    private String storageRefFingerprint(String storageRef) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(storageRef.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JRE 不支持 SHA-256", exception);
        }
    }

    private String sanitizedFailureSummary(RuntimeException exception, String storageRef) {
        String summary = exception.getMessage();
        if (summary == null || summary.isBlank()) {
            summary = exception.getClass().getSimpleName();
        }
        summary = summary.replace(storageRef, "[storage-ref]")
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .trim();
        return summary.length() <= 512 ? summary : summary.substring(0, 512);
    }

    private String normalizeCleanupStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!CLEANUP_PENDING.equals(normalized) && !CLEANUP_RESOLVED.equals(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "status 必须为 PENDING 或 RESOLVED");
        }
        return normalized;
    }

    private StorageCleanupFailureResponse toCleanupFailureResponse(
            StorageCleanupFailureRecord failure) {
        return new StorageCleanupFailureResponse(
                failure.getId(),
                failure.getResourceType(),
                failure.getResourceId(),
                failure.getStorageRefFingerprint(),
                failure.getOperation(),
                failure.getStatus(),
                failure.getFailureType(),
                failure.getFailureSummary(),
                failure.getRetryCount(),
                failure.getLastRetriedAt(),
                failure.getResolvedAt(),
                failure.getCreatedAt(),
                failure.getUpdatedAt()
        );
    }

    private static TransactionTemplate cleanupFailureTransaction(
            PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private ApiException duplicateDocument() {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.DUPLICATE_REQUEST,
                "当前工作空间已存在内容相同的资料");
    }

    private DocumentSummaryResponse toSummary(DocumentRecord document) {
        return new DocumentSummaryResponse(
                document.getId(),
                document.getWorkspaceId(),
                document.getName(),
                document.getName(),
                document.getSizeBytes(),
                document.getMediaType(),
                document.getParseStatus(),
                document.getCreatedAt()
        );
    }

    private DocumentDetailResponse toDetail(DocumentRecord document) {
        return new DocumentDetailResponse(
                document.getId(),
                document.getWorkspaceId(),
                document.getName(),
                document.getName(),
                document.getSizeBytes(),
                document.getMediaType(),
                document.getParseStatus(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }
}
