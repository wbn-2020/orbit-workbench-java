package com.orbitworkbench.mcp.application;

import com.orbitworkbench.mcp.domain.McpServerRecord;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class McpEndpointPolicy {

    public void validateSyntax(String endpointUrl) {
        URI uri = parse(endpointUrl);
        if (!"http".equalsIgnoreCase(uri.getScheme())
                && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw invalid("MCP Server 只允许 HTTP 或 HTTPS 地址");
        }
        if (uri.getHost() == null
                || uri.getHost().isBlank()
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw invalid("MCP Server 地址必须包含主机且不能包含凭据、查询参数或片段");
        }
        if (uri.getPort() < -1 || uri.getPort() > 65535) {
            throw invalid("MCP Server 端口不合法");
        }
    }

    public void assertAllowed(McpServerRecord server) {
        validateSyntax(server.getEndpointUrl());
        URI uri = parse(server.getEndpointUrl());
        try {
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (isBlocked(address, server.isAllowPrivateNetwork())) {
                    throw invalid("MCP Server 地址解析到被禁止的本地或私有网络");
                }
            }
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.MCP_SERVER_INVALID,
                    "MCP Server 地址无法解析");
        }
    }

    private boolean isBlocked(InetAddress address, boolean allowPrivateNetwork) {
        if (address.isAnyLocalAddress()
                || address.isLinkLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        if (address.isLoopbackAddress()) {
            return !allowPrivateNetwork;
        }
        return !allowPrivateNetwork
                && (address.isSiteLocalAddress() || isUniqueLocalIpv6(address));
    }

    private boolean isUniqueLocalIpv6(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
    }

    private URI parse(String endpointUrl) {
        if (endpointUrl == null || endpointUrl.isBlank()) {
            throw invalid("MCP Server 地址不能为空");
        }
        try {
            return new URI(endpointUrl.trim());
        } catch (URISyntaxException exception) {
            throw invalid("MCP Server 地址格式不合法");
        }
    }

    private ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST,
                ErrorCode.MCP_SERVER_INVALID, message);
    }
}
