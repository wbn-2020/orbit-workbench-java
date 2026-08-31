package com.orbitworkbench.project.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.orbitworkbench.project.application.GitHubRepositoryImporter.GitHubArchive;
import com.orbitworkbench.project.application.GitHubRepositoryImporter.GitHubRepository;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * 打桩传输层并记录每次实际请求的 URI，因此可以在不访问 GitHub 的前提下
 * 验证重定向白名单（SSRF 防护）、状态映射与体积上限。
 */
class GitHubArchiveClientTest {

    private static final String ZIPBALL =
            "https://api.github.com/repos/octocat/Hello-World/zipball";
    private static final String ARCHIVE =
            "https://codeload.github.com/octocat/hello-world/legacy.zip/abcdef1234567";

    private final List<URI> requested = new ArrayList<>();
    private HttpClient http;
    private GitHubArchiveClient client;
    private GitHubRepository repository;

    @BeforeEach
    void setUp() {
        http = mock(HttpClient.class);
        client = new GitHubArchiveClient(http);
        repository = new GitHubRepository("octocat", "Hello-World");
        requested.clear();
    }

    @Test
    void followsWhitelistedRedirectAndReportsRevision() throws Exception {
        stub(exchange(ZIPBALL, response(302, Map.of("location", List.of(ARCHIVE)), empty())),
                exchange(ARCHIVE, response(200, Map.of(), stream("PK fake archive"))));

        GitHubArchive archive = client.fetch(repository);

        assertEquals("octocat/Hello-World@abcdef1234567", archive.sourceFileName());
        assertTrue(new String(archive.content()).startsWith("PK "));
        assertEquals(List.of(URI.create(ZIPBALL), URI.create(ARCHIVE)), requested);
    }

    @Test
    void missingRepositoryBecomesNotFound() throws Exception {
        stub(exchange(ZIPBALL, response(404, Map.of(), empty())));

        ApiException exception = assertThrows(ApiException.class, () -> client.fetch(repository));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
        assertEquals("GitHub 公开仓库不存在或当前不可访问", exception.getMessage());
    }

    @Test
    void upstreamFailureIsNotReportedAsSuccess() throws Exception {
        stub(exchange(ZIPBALL, response(500, Map.of(), empty())));

        ApiException exception = assertThrows(ApiException.class, () -> client.fetch(repository));

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatus());
        assertEquals("GitHub 暂时无法提供仓库归档，请稍后重试", exception.getMessage());
    }

    @Test
    void redirectOffWhitelistHostIsRejectedWithoutFetchingIt() throws Exception {
        stub(exchange(ZIPBALL, response(302,
                Map.of("location", List.of("https://evil.example/internal/secret.zip")), empty())));

        ApiException exception = assertThrows(ApiException.class, () -> client.fetch(repository));

        assertEquals("GitHub 返回了不受支持的仓库归档地址", exception.getMessage());
        assertEquals(List.of(URI.create(ZIPBALL)), requested, "不得向白名单外的主机发起第二次请求");
    }

    @Test
    void archiveUrlWithQueryOrForeignRepositoryIsRejected() throws Exception {
        stub(exchange(ZIPBALL, response(302, Map.of("location",
                List.of("https://codeload.github.com/other/legacy.zip/abcdef123?token=1")), empty())));

        ApiException exception = assertThrows(ApiException.class, () -> client.fetch(repository));

        assertEquals("GitHub 返回了不受支持的仓库归档地址", exception.getMessage());
        assertEquals(1, requested.size());
    }

    @Test
    void archiveSecondHopFailureIsUpstream() throws Exception {
        stub(exchange(ZIPBALL, response(302, Map.of("location", List.of(ARCHIVE)), empty())),
                exchange(ARCHIVE, response(403, Map.of(), empty())));

        ApiException exception = assertThrows(ApiException.class, () -> client.fetch(repository));

        assertEquals("GitHub 仓库归档下载失败，请稍后重试", exception.getMessage());
    }

    @Test
    void declaredSizeAboveLimitIsRejectedBeforeReading() throws Exception {
        stub(exchange(ZIPBALL, response(302, Map.of("location", List.of(ARCHIVE)), empty())),
                exchange(ARCHIVE, response(200,
                        Map.of("content-length", List.of(String.valueOf(21L * 1024 * 1024))), empty())));

        ApiException exception = assertThrows(ApiException.class, () -> client.fetch(repository));

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, exception.getStatus());
        assertEquals(ErrorCode.FILE_TOO_LARGE, exception.getErrorCode());
        assertEquals("GitHub 仓库归档超过 20 MB 限制", exception.getMessage());
    }

    @Test
    void undeclaredStreamOverLimitIsAborted() throws Exception {
        stub(exchange(ZIPBALL, response(302, Map.of("location", List.of(ARCHIVE)), empty())),
                exchange(ARCHIVE, response(200, Map.of(), infinite())));

        ApiException exception = assertThrows(ApiException.class, () -> client.fetch(repository));

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, exception.getStatus());
        assertEquals("GitHub 仓库归档超过 20 MB 限制", exception.getMessage());
    }

    @Test
    void nonHexRevisionFallsBackToArchiveLabel() throws Exception {
        String branchArchive = "https://codeload.github.com/octocat/hello-world/legacy.zip/refs-heads-main";
        stub(exchange(ZIPBALL, response(302, Map.of("location", List.of(branchArchive)), empty())),
                exchange(branchArchive, response(200, Map.of(), stream("PK"))));

        GitHubArchive archive = client.fetch(repository);

        assertTrue(archive.sourceFileName().endsWith("@archive"), archive.sourceFileName());
    }

    @Test
    void transportFailureBecomesReadableUpstreamError() throws Exception {
        when(http.send(any(HttpRequest.class), any())).thenAnswer(invocation -> {
            requested.add(invocation.<HttpRequest>getArgument(0).uri());
            throw new IOException("connection reset");
        });

        ApiException exception = assertThrows(ApiException.class, () -> client.fetch(repository));

        assertEquals("无法连接 GitHub，请检查网络后重试", exception.getMessage());
    }

    @SafeVarargs
    private void stub(Map.Entry<String, HttpResponse<InputStream>>... exchanges) throws Exception {
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> {
                    HttpRequest request = invocation.getArgument(0);
                    requested.add(request.uri());
                    for (Map.Entry<String, HttpResponse<InputStream>> entry : exchanges) {
                        if (entry.getKey().equals(request.uri().toString())) {
                            return entry.getValue();
                        }
                    }
                    throw new AssertionError("测试未桩定该请求：" + request.uri());
                });
    }

    private Map.Entry<String, HttpResponse<InputStream>> exchange(String uri,
                                                                  HttpResponse<InputStream> response) {
        return Map.entry(uri, response);
    }

    private HttpResponse<InputStream> response(int status, Map<String, List<String>> headers,
                                               InputStream body) {
        return new FakeResponse(status, HttpHeaders.of(headers, (name, value) -> true), body);
    }

    private InputStream empty() {
        return new ByteArrayInputStream(new byte[0]);
    }

    private InputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes());
    }

    /**
     * 没有 Content-Length 却持续超标的响应体，用于验证流式读取侧的上限。
     */
    private InputStream infinite() {
        byte[] block = new byte[64 * 1024];
        return new InputStream() {
            private long written;

            @Override
            public int read() {
                throw new UnsupportedOperationException("只按块读取");
            }

            @Override
            public int read(byte[] buffer, int offset, int length) {
                written += length;
                if (written > 25L * 1024 * 1024) {
                    return -1;
                }
                System.arraycopy(block, 0, buffer, offset, length);
                return length;
            }
        };
    }

    private record FakeResponse(int statusCode, HttpHeaders headers, InputStream body)
            implements HttpResponse<InputStream> {

        @Override
        public int statusCode() {
            return statusCode;
        }

        @Override
        public HttpRequest request() {
            return null;
        }

        @Override
        public Optional<HttpResponse<InputStream>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return headers;
        }

        @Override
        public InputStream body() {
            return body;
        }

        @Override
        public Optional<javax.net.ssl.SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return null;
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
