package com.orbitworkbench.document.application;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orbitworkbench.document.domain.DocumentRecord;
import com.orbitworkbench.document.domain.StorageCleanupFailureRecord;
import com.orbitworkbench.document.infrastructure.mapper.DocumentMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.shared.config.StorageProperties;
import com.orbitworkbench.storage.application.LocalStorageService;
import com.orbitworkbench.storage.domain.StoredFile;
import com.orbitworkbench.storage.domain.TrashEntry;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentMapper documentMapper;
    @Mock
    private LocalStorageService storageService;

    private DocumentService service;

    @BeforeEach
    void setUp() {
        service = new DocumentService(
                documentMapper,
                storageService,
                new StorageProperties(Path.of("storage"), 1024, 1024));
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void rejectsStoredDocumentWithExcessiveControlCharacters() {
        MockMultipartFile upload = new MockMultipartFile(
                "file",
                "notes.txt",
                "text/plain",
                "plain text".getBytes(StandardCharsets.UTF_8));
        StoredFile stored = new StoredFile("documents/file", 10, "hash");
        when(documentMapper.countActiveWorkspace(1L)).thenReturn(1);
        when(storageService.storeDocument(any(InputStream.class), eq(1024L))).thenReturn(stored);
        when(storageService.readUtf8(stored.storageRef()))
                .thenReturn("text\u0001\u0002\u0003\u0004\u0005");

        ApiException exception = assertThrows(ApiException.class,
                () -> service.upload(1L, upload));

        assertAll(
                () -> assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, exception.getStatus()),
                () -> assertEquals(ErrorCode.UNSUPPORTED_MEDIA_TYPE, exception.getErrorCode()));
        verify(storageService).moveToTrash(stored.storageRef());
        verify(documentMapper, never()).insert(any(DocumentRecord.class));
    }

    @Test
    void restoresDocumentContentWhenTransactionRollsBack() {
        DocumentRecord document = new DocumentRecord();
        document.setId(1L);
        document.setStorageRef("documents/file");
        TrashEntry trashEntry = new TrashEntry("documents/file", ".trash/tx/documents/file");
        when(documentMapper.findByIdForUpdate(1L)).thenReturn(document);
        when(storageService.moveToTrash(document.getStorageRef())).thenReturn(trashEntry);
        when(documentMapper.deleteById(1L)).thenReturn(1);
        TransactionSynchronizationManager.initSynchronization();

        service.delete(1L);
        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(synchronization ->
                synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(storageService).restore(trashEntry);
    }

    @Test
    void uploadLocksWorkspaceBeforeInsertingDocument() {
        MockMultipartFile upload = textUpload();
        StoredFile stored = new StoredFile("documents/file", 10, "hash");
        when(documentMapper.countActiveWorkspace(1L)).thenReturn(1);
        when(storageService.storeDocument(any(InputStream.class), eq(1024L))).thenReturn(stored);
        when(storageService.readUtf8(stored.storageRef())).thenReturn("plain text");
        when(documentMapper.lockActiveWorkspace(1L)).thenReturn(1L);
        org.mockito.Mockito.doAnswer(invocation -> {
            DocumentRecord document = invocation.getArgument(0);
            document.setId(7L);
            return null;
        }).when(documentMapper).insert(any(DocumentRecord.class));

        service.upload(1L, upload);

        InOrder order = inOrder(documentMapper);
        order.verify(documentMapper).lockActiveWorkspace(1L);
        order.verify(documentMapper).findByWorkspaceAndHash(1L, "hash");
        order.verify(documentMapper).insert(any(DocumentRecord.class));
    }

    @Test
    void cleanupFailureIsInventoriedWithSanitizedReference() {
        MockMultipartFile upload = textUpload();
        StoredFile stored = new StoredFile("documents/file", 10, "hash");
        when(documentMapper.countActiveWorkspace(1L)).thenReturn(1);
        when(storageService.storeDocument(any(InputStream.class), eq(1024L))).thenReturn(stored);
        when(storageService.readUtf8(stored.storageRef())).thenReturn("plain text");
        when(documentMapper.lockActiveWorkspace(1L)).thenReturn(1L);
        when(documentMapper.findByWorkspaceAndHash(1L, "hash"))
                .thenReturn(new DocumentRecord());
        when(storageService.moveToTrash(stored.storageRef()))
                .thenThrow(new IllegalStateException("cleanup failed for documents/file"));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.upload(1L, upload));

        ArgumentCaptor<StorageCleanupFailureRecord> captor =
                ArgumentCaptor.forClass(StorageCleanupFailureRecord.class);
        verify(documentMapper).insertCleanupFailure(captor.capture());
        StorageCleanupFailureRecord failure = captor.getValue();
        assertAll(
                () -> assertEquals(ErrorCode.DUPLICATE_REQUEST, exception.getErrorCode()),
                () -> assertEquals(1, exception.getSuppressed().length),
                () -> assertEquals("PENDING", failure.getStatus()),
                () -> assertEquals("MOVE_TO_TRASH", failure.getOperation()),
                () -> assertFalse(failure.getStorageRefFingerprint().contains("documents")),
                () -> assertFalse(failure.getFailureSummary().contains("documents/file")));
    }

    @Test
    void uploadRollbackMovesStoredFileToTrash() {
        MockMultipartFile upload = textUpload();
        StoredFile stored = new StoredFile("documents/file", 10, "hash");
        when(documentMapper.countActiveWorkspace(1L)).thenReturn(1);
        when(storageService.storeDocument(any(InputStream.class), eq(1024L))).thenReturn(stored);
        when(storageService.readUtf8(stored.storageRef())).thenReturn("plain text");
        when(documentMapper.lockActiveWorkspace(1L)).thenReturn(1L);
        TransactionSynchronizationManager.initSynchronization();

        service.upload(1L, upload);
        TransactionSynchronizationManager.getSynchronizations().forEach(
                synchronization -> synchronization.afterCompletion(
                        TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(storageService).moveToTrash(stored.storageRef());
    }

    private MockMultipartFile textUpload() {
        return new MockMultipartFile(
                "file",
                "notes.txt",
                "text/plain",
                "plain text".getBytes(StandardCharsets.UTF_8));
    }
}
