package com.orbitworkbench.project.application;

import com.orbitworkbench.project.application.GitHubRepositoryImporter.GitHubArchive;
import com.orbitworkbench.project.application.GitHubRepositoryImporter.GitHubRepository;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
class GitHubArchiveClient implements GitHubRepositoryImporter.ArchiveFetcher {

    private static final long MAX_ARCHIVE_BYTES = 20L * 1024 * 1024;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;

    GitHubArchiveClient() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    GitHubArchiveClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public GitHubArchive fetch(GitHubRepository repository) {
        URI apiUri = URI.create("https://api.github.com/repos/"
                + repository.owner() + "/" + repository.name() + "/zipball");
        HttpResponse<InputStream> redirectResponse = request(apiUri);
        int status = redirectResponse.statusCode();
        if (status == 404) {
            close(redirectResponse.body());
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "GitHub 公开仓库不存在或当前不可访问");
        }
        if (status != 302 && status != 301 && status != 307 && status != 308) {
            close(redirectResponse.body());
            throw upstream("GitHub 暂时无法提供仓库归档，请稍后重试");
        }

        String location = redirectResponse.headers().firstValue("location").orElse(null);
        close(redirectResponse.body());
        URI archiveUri = location == null ? null : apiUri.resolve(location);
        if (!isExpectedArchiveUrl(archiveUri, repository)) {
            throw upstream("GitHub 返回了不受支持的仓库归档地址");
        }

        HttpResponse<InputStream> archiveResponse = request(archiveUri);
        if (archiveResponse.statusCode() != 200) {
            close(archiveResponse.body());
            throw upstream("GitHub 仓库归档下载失败，请稍后重试");
        }
        long contentLength = archiveResponse.headers()
                .firstValueAsLong("content-length")
                .orElse(-1L);
        if (contentLength > MAX_ARCHIVE_BYTES) {
            close(archiveResponse.body());
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, ErrorCode.FILE_TOO_LARGE,
                    "GitHub 仓库归档超过 20 MB 限制");
        }

        try (InputStream input = archiveResponse.body()) {
            byte[] content = readLimited(input);
            return new GitHubArchive(repository.displayName() + "@" + revisionFrom(archiveUri),
                    content);
        } catch (IOException exception) {
            throw upstream("GitHub 仓库归档读取失败，请稍后重试");
        }
    }

    private HttpResponse<InputStream> request(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "orbit-workbench-project-importer")
                .build();
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw upstream("GitHub 仓库请求已取消");
        } catch (IOException exception) {
            throw upstream("无法连接 GitHub，请检查网络后重试");
        }
    }

    private byte[] readLimited(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > MAX_ARCHIVE_BYTES) {
                throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, ErrorCode.FILE_TOO_LARGE,
                        "GitHub 仓库归档超过 20 MB 限制");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private boolean isExpectedArchiveUrl(URI uri, GitHubRepository repository) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())
                || !"codeload.github.com".equalsIgnoreCase(uri.getHost())
                || (uri.getPort() != -1 && uri.getPort() != 443)
                || uri.getRawQuery() != null || uri.getRawFragment() != null
                || uri.getRawUserInfo() != null) {
            return false;
        }
        String prefix = "/" + repository.owner().toLowerCase(Locale.ROOT)
                + "/" + repository.name().toLowerCase(Locale.ROOT) + "/legacy.zip/";
        return uri.getPath().toLowerCase(Locale.ROOT).startsWith(prefix);
    }

    private String revisionFrom(URI archiveUri) {
        String path = archiveUri.getPath();
        int index = path.lastIndexOf('/');
        String revision = index < 0 ? "" : path.substring(index + 1);
        return revision.matches("[0-9a-fA-F]{7,64}") ? revision.toLowerCase(Locale.ROOT) : "archive";
    }

    private void close(InputStream input) {
        if (input == null) {
            return;
        }
        try {
            input.close();
        } catch (IOException ignored) {
            // The response has already been handled and there is no useful recovery action.
        }
    }

    private ApiException upstream(String message) {
        return new ApiException(HttpStatus.BAD_GATEWAY, ErrorCode.UPSTREAM_UNAVAILABLE, message);
    }
}
