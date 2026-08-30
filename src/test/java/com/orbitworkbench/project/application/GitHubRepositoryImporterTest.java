package com.orbitworkbench.project.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.orbitworkbench.shared.api.ApiException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class GitHubRepositoryImporterTest {

    @Test
    void importsOnlyCanonicalPublicGitHubRepositoryUrls() {
        GitHubRepositoryImporter importer = new GitHubRepositoryImporter(repository ->
                new GitHubRepositoryImporter.GitHubArchive(
                        repository.displayName() + "@fixture",
                        "archive".getBytes(StandardCharsets.UTF_8)));

        var archive = importer.importArchive("https://github.com/spring-projects/spring-boot/");

        assertEquals("spring-projects/spring-boot@fixture", archive.sourceFileName());
        assertEquals("archive", new String(archive.content(), StandardCharsets.UTF_8));
    }

    @Test
    void rejectsNonGitHubHostsAndRepositorySubpaths() {
        GitHubRepositoryImporter importer = new GitHubRepositoryImporter(repository -> {
            throw new AssertionError("fetcher must not be called for invalid addresses");
        });

        ApiException hostException = assertThrows(ApiException.class,
                () -> importer.importArchive("https://example.com/owner/repo"));
        ApiException subpathException = assertThrows(ApiException.class,
                () -> importer.importArchive("https://github.com/owner/repo/tree/main"));
        ApiException credentialException = assertThrows(ApiException.class,
                () -> importer.importArchive("https://token@github.com/owner/repo"));

        assertEquals("仅支持 https://github.com/所有者/仓库 格式的公开仓库地址",
                hostException.getMessage());
        assertEquals("仅支持 GitHub 仓库根地址，不支持分支、目录或下载链接",
                subpathException.getMessage());
        assertEquals("仅支持 https://github.com/所有者/仓库 格式的公开仓库地址",
                credentialException.getMessage());
    }
}
