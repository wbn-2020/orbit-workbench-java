package com.orbitworkbench.storage.application;

import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.shared.config.StorageProperties;
import com.orbitworkbench.storage.domain.StoredFile;
import com.orbitworkbench.storage.domain.TrashEntry;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class LocalStorageService {

    private static final String DOCUMENTS_BUCKET = "documents";
    private static final String ARTIFACTS_BUCKET = "artifacts";
    private static final String TEMP_BUCKET = "temp";
    private static final String TRASH_BUCKET = ".trash";
    private static final int BUFFER_SIZE = 8192;

    private final Path root;
    private final Path realRoot;

    public LocalStorageService(StorageProperties properties) {
        if (properties == null || properties.root() == null) {
            throw new IllegalStateException("orbit.storage.root 必须配置");
        }
        this.root = properties.root().toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
            this.realRoot = root.toRealPath();
        } catch (IOException exception) {
            throw new IllegalStateException("本地存储根目录不可用", exception);
        }
    }

    public StoredFile storeDocument(InputStream input, long maxBytes) {
        return store(DOCUMENTS_BUCKET, input, maxBytes);
    }

    public StoredFile storeArtifact(InputStream input) {
        return store(ARTIFACTS_BUCKET, input, -1);
    }

    public String readUtf8(String storageRef) {
        Path path = resolveExisting(storageRef, false);
        try {
            byte[] content = Files.readAllBytes(path);
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                    "文件不是有效的 UTF-8 文本");
        } catch (IOException exception) {
            throw fileNotFound(storageRef, exception);
        }
    }

    public InputStream open(String storageRef) {
        Path path = resolveExisting(storageRef, false);
        try {
            return Files.newInputStream(path);
        } catch (IOException exception) {
            throw fileNotFound(storageRef, exception);
        }
    }

    public TrashEntry moveToTrash(String storageRef) {
        Path source = resolveExisting(storageRef, false);
        Path trashPath = resolveTrashDestination(storageRef);
        try {
            Files.createDirectories(trashPath.getParent());
            moveWithoutReplace(source, trashPath);
            return new TrashEntry(storageRef, toStorageRef(trashPath));
        } catch (IOException exception) {
            throw storageFailure("文件无法移动到可恢复位置", exception);
        }
    }

    public void restore(TrashEntry entry) {
        if (entry == null || entry.originalStorageRef() == null || entry.trashStorageRef() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_STORAGE_PATH,
                    "存储恢复引用无效");
        }
        Path source = resolveExisting(entry.trashStorageRef(), true);
        Path destination = resolveForStorageRef(entry.originalStorageRef(), false);
        try {
            if (Files.exists(destination)) {
                throw new IOException("恢复目标已存在");
            }
            Files.createDirectories(destination.getParent());
            assertRealPathWithinRoot(destination.getParent().toRealPath(), entry.originalStorageRef());
            moveWithoutReplace(source, destination);
        } catch (IOException exception) {
            throw storageFailure("文件无法恢复", exception);
        }
    }

    public Path resolveForStorageRef(String storageRef) {
        return resolveForStorageRef(storageRef, false);
    }

    private StoredFile store(String bucket, InputStream input, long maxBytes) {
        if (input == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "文件内容不能为空");
        }
        Path bucketPath = root.resolve(bucket).normalize();
        assertWithinRoot(bucketPath, false);
        Path temporaryPath = null;
        try {
            Files.createDirectories(bucketPath);
            assertRealPathWithinRoot(bucketPath.toRealPath(), bucket);
            Path tempPath = root.resolve(TEMP_BUCKET).normalize();
            assertWithinRoot(tempPath, false);
            Files.createDirectories(tempPath);
            assertRealPathWithinRoot(tempPath.toRealPath(), TEMP_BUCKET);
            temporaryPath = Files.createTempFile(tempPath, "storage-", ".tmp");

            MessageDigest digest = sha256();
            long size = 0;
            try (OutputStream output = Files.newOutputStream(temporaryPath)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    size += read;
                    if (maxBytes >= 0 && size > maxBytes) {
                        throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, ErrorCode.FILE_TOO_LARGE,
                                "文件超过大小限制");
                    }
                    digest.update(buffer, 0, read);
                    output.write(buffer, 0, read);
                }
            }

            String fileName = UUID.randomUUID().toString();
            Path target = bucketPath.resolve(fileName).normalize();
            assertWithinRoot(target, false);
            moveWithoutReplace(temporaryPath, target);
            assertRealPathWithinRoot(target.toRealPath(), toStorageRef(target));
            temporaryPath = null;
            return new StoredFile(toStorageRef(target), size, HexFormat.of().formatHex(digest.digest()));
        } catch (ApiException exception) {
            moveToTrashIfPresent(temporaryPath);
            throw exception;
        } catch (IOException exception) {
            moveToTrashIfPresent(temporaryPath);
            throw storageFailure("文件无法写入本地存储", exception);
        } finally {
            if (temporaryPath != null) {
                moveToTrashIfPresent(temporaryPath);
            }
        }
    }

    private Path resolveExisting(String storageRef, boolean allowTrash) {
        Path path = resolveForStorageRef(storageRef, allowTrash);
        try {
            if (!Files.isRegularFile(path)) {
                throw fileNotFound(storageRef, null);
            }
            assertRealPathWithinRoot(path.toRealPath(), storageRef);
            return path;
        } catch (IOException exception) {
            throw fileNotFound(storageRef, exception);
        }
    }

    private Path resolveForStorageRef(String storageRef, boolean allowTrash) {
        if (storageRef == null || storageRef.isBlank() || storageRef.indexOf('\0') >= 0) {
            throw invalidPath();
        }
        if (storageRef.matches("^[A-Za-z]:.*") || storageRef.startsWith("/")
                || storageRef.startsWith("\\")) {
            throw invalidPath();
        }

        final Path relative;
        try {
            relative = Path.of(storageRef);
        } catch (InvalidPathException exception) {
            throw invalidPath();
        }
        if (relative.isAbsolute()) {
            throw invalidPath();
        }

        Path normalized = root.resolve(relative).normalize();
        assertWithinRoot(normalized, allowTrash);
        return normalized;
    }

    private void assertWithinRoot(Path path, boolean allowTrash) {
        if (!path.startsWith(root) || path.equals(root)) {
            throw invalidPath();
        }
        Path relative = root.relativize(path);
        if (!allowTrash && relative.getNameCount() > 0
                && TRASH_BUCKET.equals(relative.getName(0).toString())) {
            throw invalidPath();
        }
    }

    private void assertRealPathWithinRoot(Path path, String storageRef) {
        if (!path.startsWith(realRoot)) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INVALID_STORAGE_PATH,
                    "存储引用指向非法位置: " + storageRef);
        }
    }

    private Path resolveTrashDestination(String storageRef) {
        Path source = resolveForStorageRef(storageRef, false);
        Path relative = root.relativize(source);
        Path trashDirectory = root.resolve(TRASH_BUCKET).resolve(UUID.randomUUID().toString()).normalize();
        Path destination = trashDirectory.resolve(relative).normalize();
        assertWithinRoot(destination, true);
        return destination;
    }

    private void moveWithoutReplace(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination);
        }
    }

    private void moveToTrashIfPresent(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try {
            Path relative = root.relativize(path);
            Path destination = root.resolve(TRASH_BUCKET)
                    .resolve(UUID.randomUUID().toString())
                    .resolve(relative)
                    .normalize();
            assertWithinRoot(destination, true);
            Files.createDirectories(destination.getParent());
            assertRealPathWithinRoot(destination.getParent().toRealPath(), toStorageRef(destination));
            moveWithoutReplace(path, destination);
        } catch (IOException | RuntimeException ignored) {
            // Preserve the original failure; temporary files are never permanently deleted.
        }
    }

    private String toStorageRef(Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JRE 不支持 SHA-256", exception);
        }
    }

    private ApiException invalidPath() {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_STORAGE_PATH,
                "存储路径无效");
    }

    private ApiException fileNotFound(String storageRef, IOException cause) {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.FILE_NOT_FOUND,
                "存储文件不存在");
    }

    private ApiException storageFailure(String message, IOException cause) {
        return new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INVALID_STORAGE_PATH, message);
    }
}
