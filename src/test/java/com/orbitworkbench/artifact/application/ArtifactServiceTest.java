package com.orbitworkbench.artifact.application;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.orbitworkbench.artifact.api.ArtifactDtos.ArtifactVersionSummaryResponse;
import com.orbitworkbench.artifact.api.ArtifactDtos.UpdateArtifactRequest;
import com.orbitworkbench.artifact.domain.ArtifactRecord;
import com.orbitworkbench.artifact.domain.ArtifactVersionRecord;
import com.orbitworkbench.artifact.infrastructure.mapper.ArtifactMapper;
import com.orbitworkbench.artifact.infrastructure.mapper.ArtifactVersionMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.shared.config.StorageProperties;
import com.orbitworkbench.storage.application.LocalStorageService;
import com.orbitworkbench.storage.domain.StoredFile;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class ArtifactServiceTest {

    @Mock
    private ArtifactMapper artifactMapper;
    @Mock
    private ArtifactVersionMapper artifactVersionMapper;
    @Mock
    private LocalStorageService storageService;

    private ArtifactService service;

    @BeforeEach
    void setUp() {
        service = serviceWithMaxArtifactBytes(1024);
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void initialArtifactRequiresSourceRun() {
        ApiException exception = assertThrows(ApiException.class, () ->
                service.createInitialArtifact(new CreateInitialArtifactCommand(
                        1L, 2L, null, "LEARNING_NOTE", "标题",
                        "内容", "MARKDOWN", "初始版本")));

        assertValidationFailure(exception);
        verifyNoInteractions(storageService);
    }

    @Test
    void movesStoredArtifactToTrashWhenTransactionRollsBack() {
        StoredFile storedFile = new StoredFile("artifacts/file", 7, "hash");
        when(artifactMapper.countSourceContext(1L, 2L, 3L)).thenReturn(1);
        when(storageService.storeArtifact(any())).thenReturn(storedFile);
        TransactionSynchronizationManager.initSynchronization();

        assertThrows(RuntimeException.class, () ->
                service.createInitialArtifact(new CreateInitialArtifactCommand(
                        1L, 2L, 3L, "LEARNING_NOTE", "标题",
                        "内容", "MARKDOWN", "初始版本")));
        for (TransactionSynchronization synchronization
                : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }

        verify(storageService).moveToTrash(storedFile.storageRef());
    }

    @Test
    void initialArtifactRejectsMismatchedSourceContextBeforeWritingFile() {
        when(artifactMapper.countSourceContext(1L, 2L, 3L)).thenReturn(0);

        ApiException exception = assertThrows(ApiException.class, () ->
                service.createInitialArtifact(new CreateInitialArtifactCommand(
                        1L, 2L, 3L, "LEARNING_NOTE", "标题",
                        "内容", "MARKDOWN", "初始版本")));

        assertAll(
                () -> assertEquals(HttpStatus.CONFLICT, exception.getStatus()),
                () -> assertEquals(ErrorCode.STATE_CONFLICT, exception.getErrorCode()));
        verifyNoInteractions(storageService);
    }

    @Test
    void rejectsArtifactAboveUtf8ByteLimitBeforeWritingFile() {
        ArtifactService sizeLimitedService = serviceWithMaxArtifactBytes(5);
        when(artifactMapper.countSourceContext(1L, 2L, 3L)).thenReturn(1);

        ApiException exception = assertThrows(ApiException.class, () ->
                sizeLimitedService.createInitialArtifact(new CreateInitialArtifactCommand(
                        1L, 2L, 3L, "LEARNING_NOTE", "标题",
                        "你好", "MARKDOWN", "初始版本")));

        assertAll(
                () -> assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, exception.getStatus()),
                () -> assertEquals(ErrorCode.FILE_TOO_LARGE, exception.getErrorCode()));
        verifyNoInteractions(storageService);
    }

    @Test
    void rejectsUpdatedArtifactAboveUtf8ByteLimitBeforeWritingFile() {
        ArtifactService sizeLimitedService = serviceWithMaxArtifactBytes(5);
        ArtifactRecord artifact = new ArtifactRecord();
        artifact.setId(1L);
        artifact.setCurrentVersionNumber(1);
        when(artifactMapper.findByIdForUpdate(1L)).thenReturn(artifact);

        ApiException exception = assertThrows(ApiException.class, () ->
                sizeLimitedService.update(1L, new UpdateArtifactRequest(1, "标题", "你好")));

        assertAll(
                () -> assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, exception.getStatus()),
                () -> assertEquals(ErrorCode.FILE_TOO_LARGE, exception.getErrorCode()));
        verifyNoInteractions(storageService);
    }

    @Test
    void rejectsStaleArtifactVersionBeforeWritingFile() {
        ArtifactRecord artifact = new ArtifactRecord();
        artifact.setId(1L);
        artifact.setCurrentVersionNumber(2);
        when(artifactMapper.findByIdForUpdate(1L)).thenReturn(artifact);

        ApiException exception = assertThrows(ApiException.class, () ->
                service.update(1L, new UpdateArtifactRequest(1, "标题", "正文")));

        assertAll(
                () -> assertEquals(HttpStatus.CONFLICT, exception.getStatus()),
                () -> assertEquals(ErrorCode.STATE_CONFLICT, exception.getErrorCode()));
        verifyNoInteractions(storageService);
        verifyNoInteractions(artifactVersionMapper);
    }

    @Test
    void versionListReturnsMetadataWithoutReadingContent() {
        ArtifactRecord artifact = new ArtifactRecord();
        artifact.setId(1L);
        ArtifactVersionRecord version = version(10L, 1L, 2, "artifacts/version-2");
        when(artifactMapper.findById(1L)).thenReturn(artifact);
        when(artifactVersionMapper.findByArtifactId(1L)).thenReturn(List.of(version));

        List<ArtifactVersionSummaryResponse> result = service.versions(1L);

        assertAll(
                () -> assertEquals(1, result.size()),
                () -> assertEquals(10L, result.get(0).id()),
                () -> assertEquals(2, result.get(0).version()));
        verifyNoInteractions(storageService);
    }

    @Test
    void readsOnlyRequestedArtifactVersionContent() {
        ArtifactVersionRecord version = version(10L, 1L, 2, "artifacts/version-2");
        when(artifactVersionMapper.findByArtifactIdAndId(1L, 10L)).thenReturn(version);
        when(storageService.readUtf8("artifacts/version-2")).thenReturn("版本正文");

        var result = service.version(1L, 10L);

        assertEquals("版本正文", result.content());
        verify(storageService).readUtf8("artifacts/version-2");
    }

    private ArtifactService serviceWithMaxArtifactBytes(long maxArtifactBytes) {
        return new ArtifactService(
                artifactMapper,
                artifactVersionMapper,
                storageService,
                new StorageProperties(Path.of("storage"), 1024, maxArtifactBytes));
    }

    private ArtifactVersionRecord version(Long id,
                                          Long artifactId,
                                          int versionNumber,
                                          String contentRef) {
        ArtifactVersionRecord version = new ArtifactVersionRecord();
        version.setId(id);
        version.setArtifactId(artifactId);
        version.setVersionNumber(versionNumber);
        version.setContentRef(contentRef);
        version.setContentFormat("MARKDOWN");
        version.setSourceRunId(3L);
        version.setChangeSummary("更新");
        version.setCreatedAt(Instant.parse("2026-08-19T00:00:00Z"));
        return version;
    }

    private void assertValidationFailure(ApiException exception) {
        assertAll(
                () -> assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus()),
                () -> assertEquals(ErrorCode.VALIDATION_FAILED, exception.getErrorCode()));
    }
}
