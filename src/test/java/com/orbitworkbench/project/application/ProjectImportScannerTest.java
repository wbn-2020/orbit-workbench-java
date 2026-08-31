package com.orbitworkbench.project.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orbitworkbench.shared.api.ApiException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class ProjectImportScannerTest {

    private final ProjectImportScanner scanner =
            new ProjectImportScanner(new DocumentTextExtractor());

    @Test
    void scansSupportedFilesAndInventoriesDefaultExclusions() throws Exception {
        var result = scanner.scan("demo.zip", new ByteArrayInputStream(zip(Map.of(
                "README.md", "# Demo",
                "src/main/java/Demo.java", "class Demo {}",
                "target/generated.txt", "ignored",
                ".env", "SECRET=value",
                "image.png", "not text"
        ))));

        assertEquals("ZIP", result.sourceType());
        assertEquals(5, result.files().size());
        assertEquals(2, result.parsedCount());
        assertEquals(3, result.excludedCount());
        assertEquals(0, result.failedCount());
    }

    @Test
    void rejectsZipPathTraversal() throws Exception {
        ApiException exception = assertThrows(ApiException.class,
                () -> scanner.scan("unsafe.zip", new ByteArrayInputStream(zip(Map.of(
                        "../outside.txt", "unsafe"
                )))));

        assertEquals("压缩包包含越界文件路径", exception.getMessage());
    }

    @Test
    void recordsInvalidUtf8AsFailedInsteadOfPassingItToLaterStages() {
        var result = scanner.scan("Demo.java",
                new ByteArrayInputStream(new byte[]{(byte) 0xC3, 0x28}));

        assertEquals(1, result.failedCount());
        assertEquals("FAILED", result.files().getFirst().status());
    }

    @Test
    void zipRoutesDocumentThroughExtractorAndCorruptDocumentDoesNotAbortBatch() throws Exception {
        byte[] corruptDocx = "this is definitely not an OOXML package".getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("README.md"));
            zip.write("# Demo".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("broken.docx"));
            zip.write(corruptDocx);
            zip.closeEntry();
        }

        var result = scanner.scan("mixed.zip", new ByteArrayInputStream(output.toByteArray()));

        // 文档类型不再被允许列表排除；损坏文档单独记 FAILED，同批其他文件照常解析
        assertEquals(2, result.files().size());
        assertEquals(1, result.parsedCount());
        assertEquals(1, result.failedCount());
        assertEquals(0, result.excludedCount());
        var broken = result.files().stream()
                .filter(file -> file.relativePath().equals("broken.docx")).findFirst().orElseThrow();
        assertEquals("FAILED", broken.status());
    }

    private byte[] zip(Map<String, String> entries) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    @Test
    void rejectsArchiveOverEntryCountCeiling() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (int index = 0; index <= ProjectImportScanner.MAX_FILE_COUNT; index += 1) {
                zip.putNextEntry(new ZipEntry("src/File" + index + ".java"));
                zip.write("class Demo {}".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }

        ApiException exception = assertThrows(ApiException.class, () -> scanner.scan("huge.zip",
                new ByteArrayInputStream(output.toByteArray())));

        assertEquals("压缩包文件数量超过 500 个", exception.getMessage());
    }

    @Test
    void rejectsDuplicateEntryPathsRegardlessOfCase() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (String name : new String[] {"README.md", "readme.md"}) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write("# Demo".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }

        ApiException exception = assertThrows(ApiException.class, () -> scanner.scan("dup.zip",
                new ByteArrayInputStream(output.toByteArray())));

        assertTrue(exception.getMessage().startsWith("压缩包包含重复文件路径"), exception.getMessage());
    }

    @Test
    void rejectsArchiveWithoutFiles() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("docs/"));
            zip.closeEntry();
        }

        ApiException exception = assertThrows(ApiException.class, () -> scanner.scan("empty.zip",
                new ByteArrayInputStream(output.toByteArray())));

        assertEquals("ZIP 压缩包格式无效或其中没有可扫描的文件", exception.getMessage());
    }

    @Test
    void rejectsNonZipBytes() {
        ApiException exception = assertThrows(ApiException.class, () -> scanner.scan("fake.zip",
                new ByteArrayInputStream("not a zip at all".getBytes(StandardCharsets.UTF_8))));

        assertEquals("ZIP 压缩包格式无效或其中没有可扫描的文件", exception.getMessage());
    }

    @Test
    void rejectsAbsoluteDriveNulAndOverlongEntryPaths() throws Exception {
        assertPathRejected("/etc/passwd", "压缩包包含绝对文件路径");
        assertPathRejected("C:/Windows/win.ini", "压缩包包含绝对文件路径");
        assertPathRejected("docs/nul\0name.md", "压缩包包含无效文件路径");
        assertPathRejected("docs/" + "a".repeat(1100) + ".md", "压缩包文件路径超过 1024 个字符");
    }

    @Test
    void failsEntryWhoseDeclaredSizeBreaksThePerFileCeiling() throws Exception {
        byte[] oversized = new byte[(int) ProjectImportScanner.MAX_ENTRY_BYTES + 1];
        java.util.Arrays.fill(oversized, (byte) 'a');
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("README.md"));
            zip.write("# Demo".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();

            // 只有 STORED 条目的本地头声明大小可信，用于验证读取前的 2 MB 上限拦截
            ZipEntry big = storedEntry("big.md", oversized);
            zip.putNextEntry(big);
            zip.write(oversized);
            zip.closeEntry();
        }

        var result = scanner.scan("declared.zip", new ByteArrayInputStream(output.toByteArray()));

        assertEquals(2, result.files().size());
        assertEquals(1, result.parsedCount());
        assertEquals(1, result.failedCount());
        var big = result.files().stream()
                .filter(file -> file.relativePath().equals("big.md")).findFirst().orElseThrow();
        assertEquals("FAILED", big.status());
        assertEquals("文件超过单文件 2 MB 限制", big.statusReason());
        assertEquals(oversized.length, big.sizeBytes());
    }

    @Test
    void failsCompressibleEntryThatOnlyBreaksTheCeilingWhileStreaming() throws Exception {
        byte[] compressible = new byte[(int) ProjectImportScanner.MAX_ENTRY_BYTES + 1024];
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("README.md"));
            zip.write("# Demo".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("bomb.md"));
            zip.write(compressible);
            zip.closeEntry();
        }

        var result = scanner.scan("bomb.zip", new ByteArrayInputStream(output.toByteArray()));

        // DEFLATED 条目本地头不声明大小（恒为 -1），只能边读边限；超限记 FAILED 而不是打断整批
        assertEquals(2, result.files().size());
        assertEquals(1, result.parsedCount());
        assertEquals(1, result.failedCount());
        var bomb = result.files().stream()
                .filter(file -> file.relativePath().equals("bomb.md")).findFirst().orElseThrow();
        assertEquals("FAILED", bomb.status());
        assertEquals("文件超过单文件 2 MB 限制", bomb.statusReason());
    }

    private ZipEntry storedEntry(String name, byte[] content) {
        ZipEntry entry = new ZipEntry(name);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(content.length);
        entry.setCompressedSize(content.length);
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(content);
        entry.setCrc(crc.getValue());
        return entry;
    }

    @Test
    void excludesKeyMaterialAndBuildDirectories() throws Exception {
        var result = scanner.scan("demo.zip", new ByteArrayInputStream(zip(Map.of(
                "deploy.pem", "PRIVATE KEY",
                "config/app.key", "secret",
                "node_modules/vue/index.js", "code",
                "src/App.java", "class App {}"
        ))));

        assertEquals(1, result.parsedCount());
        assertEquals(3, result.excludedCount());
        assertEquals(4, result.files().size());
    }

    @Test
    void rejectsSingleUploadOverTwoMegabytes() {
        byte[] oversized = new byte[(int) ProjectImportScanner.MAX_ENTRY_BYTES + 1];
        java.util.Arrays.fill(oversized, (byte) 'a');

        ApiException exception = assertThrows(ApiException.class,
                () -> scanner.scan("notes.md", new ByteArrayInputStream(oversized)));

        assertEquals(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE, exception.getStatus());
        assertEquals("项目文件超过单文件 2 MB 限制", exception.getMessage());
    }

    @Test
    void rejectsUnsupportedSingleFileName() {
        ApiException exception = assertThrows(ApiException.class, () -> scanner.scan("id_rsa",
                new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8))));

        assertEquals(org.springframework.http.HttpStatus.UNSUPPORTED_MEDIA_TYPE, exception.getStatus());
    }

    private void assertPathRejected(String entryName, String expectedMessage) throws Exception {
        byte[] archive = zip(Map.of(entryName, "content"));

        ApiException exception = assertThrows(ApiException.class, () -> scanner.scan("x.zip",
                new ByteArrayInputStream(archive)));

        assertEquals(expectedMessage, exception.getMessage());
    }
}
