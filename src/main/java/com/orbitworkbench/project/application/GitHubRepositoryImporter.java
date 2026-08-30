package com.orbitworkbench.project.application;

import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class GitHubRepositoryImporter {

    private final ArchiveFetcher archiveFetcher;

    @Autowired
    public GitHubRepositoryImporter(GitHubArchiveClient archiveFetcher) {
        this.archiveFetcher = archiveFetcher;
    }

    GitHubRepositoryImporter(ArchiveFetcher archiveFetcher) {
        this.archiveFetcher = archiveFetcher;
    }

    public GitHubArchive importArchive(String repositoryUrl) {
        return archiveFetcher.fetch(GitHubRepository.parse(repositoryUrl));
    }

    interface ArchiveFetcher {
        GitHubArchive fetch(GitHubRepository repository);
    }

    record GitHubArchive(String sourceFileName, byte[] content) {
        GitHubArchive {
            content = content == null ? new byte[0] : content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }

    record GitHubRepository(String owner, String name) {

        private static final Pattern OWNER = Pattern.compile("[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})");
        private static final Pattern REPOSITORY =
                Pattern.compile("[A-Za-z0-9](?:[A-Za-z0-9._-]{0,99})");

        static GitHubRepository parse(String repositoryUrl) {
            if (repositoryUrl == null || repositoryUrl.isBlank()) {
                throw validation("请输入 GitHub 公开仓库地址");
            }
            final URI uri;
            try {
                uri = new URI(repositoryUrl.trim());
            } catch (URISyntaxException exception) {
                throw validation("GitHub 仓库地址格式无效");
            }
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || !"github.com".equalsIgnoreCase(uri.getHost())
                    || (uri.getPort() != -1 && uri.getPort() != 443)
                    || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null
                    || uri.getRawFragment() != null) {
                throw validation("仅支持 https://github.com/所有者/仓库 格式的公开仓库地址");
            }

            List<String> segments = List.of(uri.getPath().split("/")).stream()
                    .filter(segment -> !segment.isBlank())
                    .toList();
            if (segments.size() != 2 || uri.getRawPath().contains("%")) {
                throw validation("仅支持 GitHub 仓库根地址，不支持分支、目录或下载链接");
            }
            String owner = segments.getFirst();
            String repository = segments.get(1);
            if (repository.endsWith(".git")) {
                repository = repository.substring(0, repository.length() - 4);
            }
            if (!OWNER.matcher(owner).matches() || !REPOSITORY.matcher(repository).matches()) {
                throw validation("GitHub 仓库所有者或名称格式无效");
            }
            return new GitHubRepository(owner, repository);
        }

        String displayName() {
            return owner + "/" + name;
        }

        private static ApiException validation(String message) {
            return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, message);
        }
    }
}
