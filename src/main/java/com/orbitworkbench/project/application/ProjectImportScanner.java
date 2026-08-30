package com.orbitworkbench.project.application;

import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ProjectImportScanner {

    private final DocumentTextExtractor textExtractor;

    public ProjectImportScanner(DocumentTextExtractor textExtractor) {
        this.textExtractor = textExtractor;
    }

    static final int MAX_FILE_COUNT = 500;
    static final long MAX_ENTRY_BYTES = 2L * 1024 * 1024;
    static final long MAX_PARSED_BYTES = 20L * 1024 * 1024;

    private static final Set<String> EXCLUDED_DIRECTORIES = Set.of(
            ".git", ".idea", ".vscode", "target", "build", "dist",
            "node_modules", "logs", "log", "cache", ".cache");
    private static final Set<String> SENSITIVE_EXTENSIONS = Set.of(
            "key", "pem", "p12", "pfx", "jks", "keystore");
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "md", "txt", "java", "xml", "yml", "yaml", "json", "sql", "properties", "gradle");
    private static final Set<String> SUPPORTED_FILE_NAMES = Set.of(
            "pom.xml", "build.gradle", "settings.gradle", "gradle.properties");

    public ScanResult scan(String sourceFileName, InputStream input) {
        if (sourceFileName == null || sourceFileName.isBlank() || input == null) {
            throw validation("导入文件不能为空");
        }
        String normalizedName = simpleFileName(sourceFileName);
        if (normalizedName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            return scanZip(input);
        }
        return scanSingleFile(normalizedName, input);
    }

    private ScanResult scanZip(InputStream input) {
        List<ScannedFile> files = new ArrayList<>();
        Set<String> paths = new HashSet<>();
        long parsedBytes = 0;
        int fileCount = 0;
        try (ZipInputStream zip = new ZipInputStream(input, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                fileCount++;
                if (fileCount > MAX_FILE_COUNT) {
                    throw validation("压缩包文件数量超过 " + MAX_FILE_COUNT + " 个");
                }

                String relativePath = safeRelativePath(entry.getName());
                if (!paths.add(relativePath.toLowerCase(Locale.ROOT))) {
                    throw validation("压缩包包含重复文件路径: " + relativePath);
                }

                String exclusionReason = exclusionReason(relativePath);
                if (exclusionReason != null) {
                    files.add(ScannedFile.excluded(relativePath, Math.max(entry.getSize(), 0),
                            exclusionReason));
                    continue;
                }

                if (entry.getSize() > MAX_ENTRY_BYTES) {
                    files.add(ScannedFile.failed(relativePath, entry.getSize(), mediaType(relativePath),
                            "文件超过单文件 2 MB 限制"));
                    continue;
                }

                byte[] content = readLimited(zip, MAX_ENTRY_BYTES);
                parsedBytes += content.length;
                if (parsedBytes > MAX_PARSED_BYTES) {
                    throw validation("压缩包可解析文本总量超过 20 MB");
                }
                files.add(classifyEntry(relativePath, content));
            }
        } catch (ApiException exception) {
            throw exception;
        } catch (ZipException exception) {
            throw validation("ZIP 压缩包格式无效");
        } catch (IOException exception) {
            throw validation("无法读取项目压缩包");
        }
        if (fileCount == 0) {
            throw validation("压缩包中没有可扫描的文件");
        }
        return new ScanResult("ZIP", List.copyOf(files));
    }

    private ScanResult scanSingleFile(String fileName, InputStream input) {
        String exclusionReason = exclusionReason(fileName);
        if (exclusionReason != null) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    ErrorCode.UNSUPPORTED_MEDIA_TYPE, exclusionReason);
        }
        try {
            byte[] content = readLimited(input, MAX_ENTRY_BYTES);
            return new ScanResult("FILE", List.of(classifyEntry(fileName, content)));
        } catch (ApiException exception) {
            throw exception;
        } catch (IOException exception) {
            throw validation("无法读取上传文件");
        }
    }

    /** 文档类型走文本抽取（PDF/DOCX/PPTX/XLSX），其余按 UTF-8 文本校验。 */
    private ScannedFile classifyEntry(String relativePath, byte[] content) {
        if (textExtractor.supports(relativePath)) {
            try {
                String text = textExtractor.extract(relativePath, new ByteArrayInputStream(content));
                if (text == null || text.isBlank()) {
                    return ScannedFile.failed(relativePath, content.length,
                            textExtractor.mediaType(relativePath), "文档中未提取到文本内容");
                }
                return ScannedFile.parsed(relativePath, text.getBytes(StandardCharsets.UTF_8),
                        "text/plain");
            } catch (IOException | RuntimeException exception) {
                return ScannedFile.failed(relativePath, content.length,
                        textExtractor.mediaType(relativePath),
                        "文档解析失败：文件损坏或格式不受支持");
            }
        }
        return validateUtf8(relativePath, content);
    }

    private ScannedFile validateUtf8(String relativePath, byte[] content) {
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content));
            return ScannedFile.parsed(relativePath, content, mediaType(relativePath));
        } catch (java.nio.charset.CharacterCodingException exception) {
            return ScannedFile.failed(relativePath, content.length, mediaType(relativePath),
                    "文件不是有效的 UTF-8 文本");
        }
    }

    private byte[] readLimited(InputStream input, long limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > limit) {
                throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, ErrorCode.FILE_TOO_LARGE,
                        "项目文件超过单文件 2 MB 限制");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private String safeRelativePath(String entryName) {
        if (entryName == null || entryName.isBlank() || entryName.indexOf('\0') >= 0) {
            throw validation("压缩包包含无效文件路径");
        }
        String portable = entryName.replace('\\', '/');
        if (portable.startsWith("/") || portable.matches("^[A-Za-z]:.*")) {
            throw validation("压缩包包含绝对文件路径");
        }
        final Path normalized;
        try {
            normalized = Path.of(portable).normalize();
        } catch (InvalidPathException exception) {
            throw validation("压缩包包含无效文件路径");
        }
        if (normalized.isAbsolute() || normalized.getNameCount() == 0
                || normalized.startsWith("..")) {
            throw validation("压缩包包含越界文件路径");
        }
        String value = normalized.toString().replace('\\', '/');
        if (value.length() > 1024) {
            throw validation("压缩包文件路径超过 1024 个字符");
        }
        return value;
    }

    private String simpleFileName(String fileName) {
        String portable = fileName.replace('\\', '/');
        int separator = portable.lastIndexOf('/');
        String value = portable.substring(separator + 1).trim();
        if (value.isEmpty() || value.length() > 255 || value.indexOf('\0') >= 0) {
            throw validation("文件名长度必须为 1-255");
        }
        return value;
    }

    private String exclusionReason(String relativePath) {
        String lower = relativePath.toLowerCase(Locale.ROOT);
        String[] segments = lower.split("/");
        for (String segment : segments) {
            if (EXCLUDED_DIRECTORIES.contains(segment)) {
                return "默认排除目录: " + segment;
            }
        }
        String fileName = segments[segments.length - 1];
        if (fileName.equals(".env") || fileName.startsWith(".env.")) {
            return "环境变量文件不允许导入";
        }
        String extension = extension(fileName);
        if (SENSITIVE_EXTENSIONS.contains(extension)) {
            return "密钥或证书文件不允许导入";
        }
        if (!isSupported(fileName, extension)) {
            return "当前版本不解析该文件类型";
        }
        return null;
    }

    private boolean isSupported(String fileName, String extension) {
        return SUPPORTED_EXTENSIONS.contains(extension)
                || SUPPORTED_FILE_NAMES.contains(fileName)
                || textExtractor.supports(fileName)
                || fileName.equals("readme")
                || fileName.startsWith("readme.");
    }

    private String extension(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index < 0 || index == fileName.length() - 1 ? "" : fileName.substring(index + 1);
    }

    private String mediaType(String path) {
        String extension = extension(path.toLowerCase(Locale.ROOT));
        return switch (extension) {
            case "md" -> "text/markdown";
            case "json" -> "application/json";
            case "xml" -> "application/xml";
            case "yml", "yaml" -> "application/yaml";
            case "sql" -> "application/sql";
            case "java" -> "text/x-java-source";
            default -> "text/plain";
        };
    }

    private ApiException validation(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, message);
    }

    public record ScanResult(String sourceType, List<ScannedFile> files) {
        public int parsedCount() {
            return (int) files.stream().filter(file -> "PARSED".equals(file.status())).count();
        }

        public int excludedCount() {
            return (int) files.stream().filter(file -> "EXCLUDED".equals(file.status())).count();
        }

        public int failedCount() {
            return (int) files.stream().filter(file -> "FAILED".equals(file.status())).count();
        }

        public long totalSizeBytes() {
            return files.stream().mapToLong(ScannedFile::sizeBytes).sum();
        }
    }

    public record ScannedFile(
            String relativePath,
            String mediaType,
            byte[] content,
            long sizeBytes,
            String status,
            String statusReason
    ) {
        static ScannedFile parsed(String path, byte[] content, String mediaType) {
            return new ScannedFile(path, mediaType, content, content.length, "PARSED", null);
        }

        static ScannedFile excluded(String path, long sizeBytes, String reason) {
            return new ScannedFile(path, null, null, sizeBytes, "EXCLUDED", reason);
        }

        static ScannedFile failed(String path, long sizeBytes, String mediaType, String reason) {
            return new ScannedFile(path, mediaType, null, sizeBytes, "FAILED", reason);
        }
    }
}
