package com.orbitworkbench.mcp.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.orbitworkbench.mcp.application.McpClient;
import com.orbitworkbench.mcp.application.McpEndpointPolicy;
import com.orbitworkbench.mcp.application.McpToolDescriptor;
import com.orbitworkbench.mcp.domain.McpServerRecord;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.shared.config.McpProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

@Component
public class McpJsonRpcClient implements McpClient {

    private static final String SESSION_HEADER = "Mcp-Session-Id";
    private static final String PROTOCOL_HEADER = "MCP-Protocol-Version";

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final McpEndpointPolicy endpointPolicy;
    private final McpProperties properties;
    private final AtomicLong requestSequence = new AtomicLong();

    public McpJsonRpcClient(WebClient.Builder webClientBuilder,
                            ObjectMapper objectMapper,
                            McpEndpointPolicy endpointPolicy,
                            McpProperties properties) {
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
        this.endpointPolicy = endpointPolicy;
        this.properties = properties;
    }

    @Override
    public List<McpToolDescriptor> listTools(McpServerRecord server) {
        endpointPolicy.assertAllowed(server);
        Duration timeout = normalizedTimeout(properties.getRequestTimeout());
        int maxBytes = normalizedMaxBytes(properties.getMaxResponseBytes());
        Session session = initialize(server, timeout, maxBytes);
        List<McpToolDescriptor> tools = new ArrayList<>();
        String cursor = null;
        boolean finished = false;
        for (int page = 0; page < 10; page++) {
            ObjectNode params = objectMapper.createObjectNode();
            if (cursor != null) {
                params.put("cursor", cursor);
            }
            ObjectNode listRequest = request("tools/list", params);
            RpcResponse response = post(server, listRequest, session, timeout, maxBytes);
            JsonNode result = requireResult(response.body(), listRequest.get("id"));
            JsonNode toolNodes = result.path("tools");
            if (!toolNodes.isArray()) {
                throw protocol("MCP tools/list 响应缺少 tools 数组");
            }
            for (JsonNode tool : toolNodes) {
                if (tools.size() >= Math.max(1, properties.getMaxTools())) {
                    throw protocol("MCP 工具数量超过限制");
                }
                tools.add(toDescriptor(tool));
            }
            JsonNode nextCursor = result.get("nextCursor");
            if (nextCursor == null || nextCursor.isNull()
                    || nextCursor.asText().isBlank()) {
                finished = true;
                break;
            }
            cursor = nextCursor.asText();
        }
        if (!finished) {
            throw protocol("MCP tools/list 分页超过最大页数限制");
        }
        return List.copyOf(tools);
    }

    @Override
    public JsonNode callTool(McpServerRecord server,
                             String toolName,
                             JsonNode arguments,
                             Duration timeout,
                             int maxResponseBytes) {
        endpointPolicy.assertAllowed(server);
        Session session = initialize(
                server,
                normalizedTimeout(timeout),
                normalizedMaxBytes(maxResponseBytes));
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", toolName);
        params.set("arguments", arguments == null
                ? objectMapper.createObjectNode() : arguments);
        ObjectNode callRequest = request("tools/call", params);
        RpcResponse response = post(
                server,
                callRequest,
                session,
                normalizedTimeout(timeout),
                normalizedMaxBytes(maxResponseBytes));
        JsonNode result = requireResult(response.body(), callRequest.get("id"));
        if (result.path("isError").asBoolean(false)) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    ErrorCode.MCP_PROTOCOL_ERROR,
                    "MCP 工具返回错误");
        }
        JsonNode structuredContent = result.get("structuredContent");
        if (structuredContent != null && !structuredContent.isNull()) {
            return structuredContent;
        }
        JsonNode content = result.get("content");
        return content == null || content.isNull() ? result : content;
    }

    private Session initialize(McpServerRecord server,
                               Duration timeout,
                               int maxBytes) {
        ObjectNode initializeRequest = request("initialize", objectMapper.valueToTree(Map.of(
                "protocolVersion", configuredProtocolVersion(),
                "capabilities", Map.of(),
                "clientInfo", Map.of(
                        "name", "orbit-workbench",
                        "version", "0.1.0"))));
        RpcResponse response = post(
                server,
                initializeRequest,
                Session.empty(),
                timeout,
                maxBytes);
        JsonNode result = requireResult(response.body(), initializeRequest.get("id"));
        String protocolVersion = result.path("protocolVersion").asText(null);
        if (protocolVersion == null || protocolVersion.isBlank()) {
            throw protocol("MCP initialize 响应缺少 protocolVersion");
        }
        sendInitialized(server, new Session(
                response.headers().getFirst(SESSION_HEADER),
                protocolVersion), timeout, maxBytes);
        return new Session(
                response.headers().getFirst(SESSION_HEADER),
                protocolVersion);
    }

    private void sendInitialized(McpServerRecord server,
                                 Session session,
                                 Duration timeout,
                                 int maxBytes) {
        ObjectNode notification = objectMapper.createObjectNode();
        notification.put("jsonrpc", "2.0");
        notification.put("method", "notifications/initialized");
        RequestSpec spec = requestSpec(server, notification, session, maxBytes);
        try {
            spec.client().post()
                    .uri(server.getEndpointUrl())
                    .headers(headers -> headers.addAll(spec.headers()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                    .bodyValue(notification)
                    .exchangeToMono(this::readNotificationResponse)
                    .block(timeout);
        } catch (ApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    private RpcResponse post(McpServerRecord server,
                             ObjectNode request,
                             Session session,
                             Duration timeout,
                             int maxBytes) {
        RequestSpec spec = requestSpec(server, request, session, maxBytes);
        try {
            return spec.client().post()
                    .uri(server.getEndpointUrl())
                    .headers(headers -> headers.addAll(spec.headers()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                    .bodyValue(request)
                    .exchangeToMono(response -> readResponse(response, maxBytes))
                    .block(timeout);
        } catch (DataBufferLimitException exception) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE,
                    ErrorCode.MCP_PROTOCOL_ERROR,
                    "MCP 响应超过大小限制");
        } catch (WebClientRequestException exception) {
            throw unavailable(exception);
        } catch (ApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    private Mono<RpcResponse> readResponse(ClientHttpResponse response,
                                           int maxBytes) {
        HttpStatusCode status = response.getStatusCode();
        if (status.is3xxRedirection()) {
            return response.releaseBody().then(Mono.error(new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    ErrorCode.MCP_SERVER_INVALID,
                    "MCP Server 禁止重定向")));
        }
        int[] bytesRead = {0};
        return response.getBody()
                .reduce(new StringBuilder(),
                        (builder, buffer) -> {
                            byte[] bytes = new byte[buffer.readableByteCount()];
                            buffer.read(bytes);
                            DataBufferUtils.release(buffer);
                            bytesRead[0] += bytes.length;
                            if (bytesRead[0] > maxBytes) {
                                throw new ApiException(
                                        HttpStatus.PAYLOAD_TOO_LARGE,
                                        ErrorCode.MCP_PROTOCOL_ERROR,
                                        "MCP 响应超过大小限制");
                            }
                            return builder.append(new String(
                                    bytes, java.nio.charset.StandardCharsets.UTF_8));
                        })
                .map(StringBuilder::toString)
                .defaultIfEmpty("")
                .map(body -> {
                    if (!status.is2xxSuccessful()) {
                        throw new ApiException(
                                HttpStatus.BAD_GATEWAY,
                                ErrorCode.MCP_SERVER_UNAVAILABLE,
                                "MCP Server 返回 HTTP " + status.value());
                    }
                    return new RpcResponse(
                            body,
                            response.getHeaders());
                });
    }

    private Mono<Void> readNotificationResponse(ClientHttpResponse response) {
        if (!response.getStatusCode().is2xxSuccessful()) {
            return response.releaseBody().then(Mono.error(new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    ErrorCode.MCP_SERVER_UNAVAILABLE,
                    "MCP initialized 通知失败")));
        }
        return response.releaseBody();
    }

    private RequestSpec requestSpec(McpServerRecord server,
                                    ObjectNode request,
                                    Session session,
                                    int maxBytes) {
        endpointPolicy.assertAllowed(server);
        HttpHeaders headers = new HttpHeaders();
        if (session.sessionId() != null && !session.sessionId().isBlank()) {
            headers.set(SESSION_HEADER, session.sessionId());
        }
        if (session.protocolVersion() != null
                && !session.protocolVersion().isBlank()) {
            headers.set(PROTOCOL_HEADER, session.protocolVersion());
        }
        WebClient client = webClientBuilder.clone()
                .codecs(configurer -> configurer.defaultCodecs()
                        .maxInMemorySize(Math.max(1024, maxBytes)))
                .build();
        return new RequestSpec(client, headers);
    }

    private ObjectNode request(String method, JsonNode params) {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", requestSequence.incrementAndGet());
        request.put("method", method);
        request.set("params", params);
        return request;
    }

    private JsonNode requireResult(String body, JsonNode expectedId) {
        JsonNode envelope = parseBody(body);
        if (!"2.0".equals(envelope.path("jsonrpc").asText())
                || envelope.get("id") == null
                || expectedId == null
                || !expectedId.equals(envelope.get("id"))) {
            throw protocol("MCP 响应不是有效 JSON-RPC 响应");
        }
        JsonNode error = envelope.get("error");
        if (error != null && !error.isNull()) {
            throw protocol("MCP Server 返回协议错误");
        }
        JsonNode result = envelope.get("result");
        if (result == null || result.isNull()) {
            throw protocol("MCP 响应缺少 result");
        }
        return result;
    }

    private JsonNode parseBody(String body) {
        if (body == null || body.isBlank()) {
            throw protocol("MCP 响应为空");
        }
        String candidate = body.trim();
        if (candidate.contains("data:")) {
            String[] lines = candidate.split("\\R");
            for (int index = lines.length - 1; index >= 0; index--) {
                String line = lines[index].trim();
                if (line.startsWith("data:")) {
                    candidate = line.substring(5).trim();
                    if (!candidate.isBlank() && !"[DONE]".equals(candidate)) {
                        break;
                    }
                }
            }
        }
        try {
            return objectMapper.readTree(candidate);
        } catch (JsonProcessingException exception) {
            throw protocol("MCP 响应不是有效 JSON");
        }
    }

    private McpToolDescriptor toDescriptor(JsonNode tool) {
        String name = tool.path("name").asText(null);
        if (name == null || name.isBlank()) {
            throw protocol("MCP 工具缺少 name");
        }
        JsonNode inputSchema = tool.get("inputSchema");
        if (inputSchema == null || !inputSchema.isObject()) {
            inputSchema = objectMapper.createObjectNode();
        }
        JsonNode outputSchema = tool.get("outputSchema");
        if (outputSchema == null || !outputSchema.isObject()) {
            outputSchema = objectMapper.createObjectNode();
        }
        return new McpToolDescriptor(
                name,
                text(tool, "title"),
                text(tool, "description"),
                inputSchema,
                outputSchema);
    }

    private String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String configuredProtocolVersion() {
        return properties.getProtocolVersion() == null
                || properties.getProtocolVersion().isBlank()
                ? "2025-06-18" : properties.getProtocolVersion().trim();
    }

    private Duration normalizedTimeout(Duration value) {
        return value == null || value.isZero() || value.isNegative()
                ? Duration.ofSeconds(30) : value;
    }

    private int normalizedMaxBytes(int value) {
        return Math.max(1024, value);
    }

    private ApiException unavailable(Throwable cause) {
        return new ApiException(HttpStatus.BAD_GATEWAY,
                ErrorCode.MCP_SERVER_UNAVAILABLE,
                "MCP Server 暂时不可用");
    }

    private ApiException protocol(String message) {
        return new ApiException(HttpStatus.BAD_GATEWAY,
                ErrorCode.MCP_PROTOCOL_ERROR, message);
    }

    private record Session(String sessionId, String protocolVersion) {
        private static Session empty() {
            return new Session(null, null);
        }
    }

    private record RpcResponse(String body, HttpHeaders headers) {
    }

    private record RequestSpec(WebClient client, HttpHeaders headers) {
    }
}
