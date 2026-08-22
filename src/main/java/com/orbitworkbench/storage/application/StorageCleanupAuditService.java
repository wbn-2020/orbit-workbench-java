package com.orbitworkbench.storage.application;

import com.orbitworkbench.document.domain.StorageCleanupFailureRecord;
import com.orbitworkbench.document.infrastructure.mapper.DocumentMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class StorageCleanupAuditService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(StorageCleanupAuditService.class);

    private final DocumentMapper documentMapper;
    private final TransactionTemplate requiresNew;

    public StorageCleanupAuditService(DocumentMapper documentMapper,
                                      PlatformTransactionManager transactionManager) {
        this.documentMapper = documentMapper;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void record(String resourceType,
                       Long resourceId,
                       String storageRef,
                       RuntimeException exception) {
        Instant now = Instant.now();
        StorageCleanupFailureRecord failure = new StorageCleanupFailureRecord();
        failure.setResourceType(resourceType);
        failure.setResourceId(resourceId);
        failure.setStorageRef(storageRef);
        failure.setStorageRefFingerprint(fingerprint(storageRef));
        failure.setOperation("MOVE_TO_TRASH");
        failure.setStatus("PENDING");
        failure.setFailureType(trim(
                exception.getClass().getSimpleName(), 128, "RuntimeException"));
        failure.setFailureSummary(trim(
                exception.getMessage(), 512, "存储文件移动到回收区失败"));
        failure.setCreatedAt(now);
        failure.setUpdatedAt(now);
        try {
            requiresNew.executeWithoutResult(status ->
                    documentMapper.insertCleanupFailure(failure));
        } catch (RuntimeException persistenceException) {
            LOGGER.error(
                    "Storage cleanup audit failed: refFingerprint={}, failureType={}",
                    failure.getStorageRefFingerprint(),
                    persistenceException.getClass().getSimpleName());
            exception.addSuppressed(persistenceException);
        }
    }

    private String fingerprint(String storageRef) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(storageRef.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private String trim(String value, int maxLength, String fallback) {
        String normalized = value == null || value.isBlank()
                ? fallback
                : value.replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .trim();
        return normalized.length() <= maxLength
                ? normalized : normalized.substring(0, maxLength);
    }
}
