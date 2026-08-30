package com.orbitworkbench.project.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
