package com.orbitworkbench.aiconnection.domain;

import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ConnectionAddressNormalizer {

    public NormalizedAddress normalize(String rawBaseUrl, String rawEndpointPath, AiProtocol protocol) {
        String baseValue = required(rawBaseUrl, "baseUrl");
        String endpointValue = rawEndpointPath == null ? "" : rawEndpointPath.trim();
        URI base = parseBase(baseValue);
        String basePath = normalizePath(base.getPath());

        if (isAbsolute(endpointValue)) {
            URI endpoint = parseBase(endpointValue);
            if (!sameOrigin(base, endpoint)) {
                throw invalid("endpointPath 必须与 baseUrl 使用相同的地址");
            }
            String absoluteEndpointPath = normalizePath(endpoint.getPath());
            if (!basePath.isBlank() && absoluteEndpointPath.startsWith(basePath + "/")) {
                endpointValue = absoluteEndpointPath.substring(basePath.length());
            } else if (!basePath.isBlank() && absoluteEndpointPath.equals(basePath)) {
                endpointValue = endpointSuffix(absoluteEndpointPath, protocol);
                basePath = basePath.substring(0, basePath.length() - endpointValue.length());
            } else {
                endpointValue = absoluteEndpointPath;
            }
        }

        String endpointPath = endpointValue.isBlank()
                ? defaultEndpoint(protocol)
                : normalizeEndpoint(endpointValue);
        String lowerBasePath = basePath.toLowerCase(Locale.ROOT);
        if (endpointValue.isBlank() && lowerBasePath.endsWith("/chat/completions")) {
            endpointPath = "/chat/completions";
        } else if (endpointValue.isBlank() && lowerBasePath.endsWith("/responses")) {
            endpointPath = "/responses";
        }

        String normalizedBase = buildBaseUrl(base, basePath);
        return new NormalizedAddress(normalizedBase, endpointPath);
    }

    public URI requestUri(String baseUrl, String endpointPath) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String path = endpointPath.startsWith("/") ? endpointPath : "/" + endpointPath;
        if (URI.create(base).getPath().endsWith(path)) {
            return URI.create(base);
        }
        return URI.create(base + path);
    }

    private URI parseBase(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))
                    || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null) {
                throw invalid("地址必须是没有凭据、查询参数和片段的 HTTP(S) URL");
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw invalid("baseUrl 不是有效的 HTTP(S) URL");
        }
    }

    private String normalizeEndpoint(String value) {
        if (value.contains("?") || value.contains("#") || value.contains(" ") || value.contains("\t")) {
            throw invalid("endpointPath 不能包含查询参数、片段或空白字符");
        }
        String path = normalizePath(value);
        if ("/".equals(path)) {
            throw invalid("endpointPath 不能为空路径");
        }
        return path;
    }

    private String buildBaseUrl(URI base, String path) {
        try {
            return new URI(base.getScheme().toLowerCase(Locale.ROOT), null,
                    base.getHost(), base.getPort(), path, null, null).toString();
        } catch (URISyntaxException exception) {
            throw invalid("baseUrl 规范化失败");
        }
    }

    private boolean sameOrigin(URI left, URI right) {
        return left.getScheme().equalsIgnoreCase(right.getScheme())
                && left.getHost().equalsIgnoreCase(right.getHost())
                && left.getPort() == right.getPort();
    }

    private String defaultEndpoint(AiProtocol protocol) {
        return protocol == AiProtocol.RESPONSES ? "/responses" : "/chat/completions";
    }

    private String endpointSuffix(String path, AiProtocol protocol) {
        if (path.endsWith("/chat/completions")) {
            return "/chat/completions";
        }
        if (path.endsWith("/responses")) {
            return "/responses";
        }
        return defaultEndpoint(protocol);
    }

    private String normalizePath(String value) {
        if (value == null || value.isBlank() || "/".equals(value.trim())) {
            return "";
        }
        String path = value.trim().replace('\\', '/');
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        while (path.contains("//")) {
            path = path.replace("//", "/");
        }
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    private boolean isAbsolute(String value) {
        return value.startsWith("http://") || value.startsWith("https://");
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw invalid(field + " 不能为空");
        }
        return value.trim();
    }

    private ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, message);
    }

    public record NormalizedAddress(String baseUrl, String endpointPath) {
    }
}
