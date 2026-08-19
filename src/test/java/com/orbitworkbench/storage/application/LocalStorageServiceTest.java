package com.orbitworkbench.storage.application;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.shared.config.StorageProperties;
import com.orbitworkbench.storage.domain.StoredFile;
import com.orbitworkbench.storage.domain.TrashEntry;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

class LocalStorageServiceTest {

    private static final String HELLO_SHA256 =
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";

    @TempDir
    Path tempDir;

    @Test
    void resolvesOnlyPathsBelowConfiguredRoot() {
        Path storageRoot = tempDir.resolve("storage");
        LocalStorageService service = service(storageRoot);

        Path resolved = service.resolveForStorageRef("documents/nested/file.txt");

        assertEquals(storageRoot.resolve("documents/nested/file.txt").toAbsolutePath().normalize(), resolved);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            ".",
            "..",
            "../outside.txt",
            "documents/../../outside.txt",
            "/absolute/file.txt",
            "\\absolute\\file.txt",
            "C:\\outside\\file.txt",
            ".trash/file.txt"
    })
    void rejectsInvalidOrEscapingStorageReferences(String storageRef) {
        LocalStorageService service = service(tempDir.resolve("storage"));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.resolveForStorageRef(storageRef));

        assertApiException(exception, HttpStatus.BAD_REQUEST, ErrorCode.INVALID_STORAGE_PATH);
    }

    @Test
    void storesAndReadsDocumentWithinRoot() {
        LocalStorageService service = service(tempDir.resolve("storage"));

        StoredFile stored = service.storeDocument(
                new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)),
                5);

        assertAll(
                () -> assertTrue(stored.storageRef().startsWith("documents/")),
                () -> assertEquals(5, stored.sizeBytes()),
                () -> assertEquals(HELLO_SHA256, stored.contentHash()),
                () -> assertEquals("hello", service.readUtf8(stored.storageRef())),
                () -> assertTrue(Files.isRegularFile(service.resolveForStorageRef(stored.storageRef()))));
    }

    @Test
    void rejectsDocumentLargerThanLimit() {
        LocalStorageService service = service(tempDir.resolve("storage"));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.storeDocument(
                        new ByteArrayInputStream("1234".getBytes(StandardCharsets.UTF_8)),
                        3));

        assertApiException(exception, HttpStatus.PAYLOAD_TOO_LARGE, ErrorCode.FILE_TOO_LARGE);
    }

    @Test
    void rejectsMalformedUtf8AfterDocumentIsStored() {
        LocalStorageService service = service(tempDir.resolve("storage"));
        StoredFile stored = service.storeDocument(
                new ByteArrayInputStream(new byte[]{(byte) 0xC3, 0x28}),
                2);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.readUtf8(stored.storageRef()));

        assertAll(
                () -> assertTrue(Files.isRegularFile(
                        service.resolveForStorageRef(stored.storageRef()))),
                () -> assertApiException(exception, HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                        ErrorCode.UNSUPPORTED_MEDIA_TYPE));
    }

    @Test
    void trashReferencesStayInternalAndCanBeRestored() {
        LocalStorageService service = service(tempDir.resolve("storage"));
        StoredFile stored = service.storeArtifact(
                new ByteArrayInputStream("artifact".getBytes(StandardCharsets.UTF_8)));

        TrashEntry trashEntry = service.moveToTrash(stored.storageRef());

        ApiException hiddenTrash = assertThrows(ApiException.class,
                () -> service.resolveForStorageRef(trashEntry.trashStorageRef()));
        ApiException missingOriginal = assertThrows(ApiException.class,
                () -> service.readUtf8(stored.storageRef()));

        assertAll(
                () -> assertEquals(stored.storageRef(), trashEntry.originalStorageRef()),
                () -> assertTrue(trashEntry.trashStorageRef().startsWith(".trash/")),
                () -> assertApiException(hiddenTrash, HttpStatus.BAD_REQUEST,
                        ErrorCode.INVALID_STORAGE_PATH),
                () -> assertApiException(missingOriginal, HttpStatus.NOT_FOUND,
                        ErrorCode.FILE_NOT_FOUND));

        service.restore(trashEntry);

        assertEquals("artifact", service.readUtf8(stored.storageRef()));
    }

    @Test
    void rejectsExistingFileReachedThroughSymlinkOutsideRoot() throws IOException {
        Path storageRoot = tempDir.resolve("storage");
        LocalStorageService service = service(storageRoot);
        Path outside = tempDir.resolve("outside");
        Files.createDirectories(outside);
        Files.writeString(outside.resolve("secret.txt"), "outside", StandardCharsets.UTF_8);
        Path link = storageRoot.resolve("linked-outside");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (IOException | UnsupportedOperationException | SecurityException exception) {
            Assumptions.assumeTrue(false, "Symbolic links are unavailable: " + exception.getMessage());
        }

        ApiException exception = assertThrows(ApiException.class,
                () -> service.readUtf8("linked-outside/secret.txt"));

        assertApiException(exception, HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INVALID_STORAGE_PATH);
    }

    private LocalStorageService service(Path root) {
        return new LocalStorageService(new StorageProperties(root, 1024, 1024));
    }

    private void assertApiException(ApiException exception,
                                    HttpStatus expectedStatus,
                                    ErrorCode expectedErrorCode) {
        assertAll(
                () -> assertEquals(expectedStatus, exception.getStatus()),
                () -> assertEquals(expectedErrorCode, exception.getErrorCode()));
    }
}
