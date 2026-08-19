package com.orbitworkbench.aiconnection.application;

import com.orbitworkbench.ai.application.AiInvocation;
import com.orbitworkbench.ai.application.AiProviderException;
import com.orbitworkbench.ai.application.AiStreamEvent;
import com.orbitworkbench.ai.application.ModelGateway;
import com.orbitworkbench.aiconnection.api.AiConnectionDtos.ConnectionRequest;
import com.orbitworkbench.aiconnection.api.AiConnectionDtos.ConnectionResponse;
import com.orbitworkbench.aiconnection.api.AiConnectionDtos.ConnectionTestResponse;
import com.orbitworkbench.aiconnection.api.AiConnectionDtos.DraftTestRequest;
import com.orbitworkbench.aiconnection.api.AiConnectionDtos.SavedTestRequest;
import com.orbitworkbench.aiconnection.api.AiConnectionDtos.UpdateConnectionRequest;
import com.orbitworkbench.aiconnection.domain.AiConnectionRecord;
import com.orbitworkbench.aiconnection.domain.ConnectionTestRecord;
import com.orbitworkbench.aiconnection.infrastructure.mapper.AiConnectionMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.shared.api.PageResult;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AiConnectionService {
    private static final int CONNECTION_TEST_MAX_OUTPUT_TOKENS = 128;
    private static final ConnectionTestLimits DEFAULT_TEST_LIMITS =
            new ConnectionTestLimits(
                    512,
                    64 * 1024,
                    128 * 1024,
                    Duration.ofMinutes(2),
                    Duration.ofSeconds(1));

    private final AiConnectionMapper mapper;
    private final CredentialCipher cipher;
    private final ModelGateway gateway;
    private final TransactionTemplate transactionTemplate;
    private final ConnectionTestLimits testLimits;

    public AiConnectionService(AiConnectionMapper mapper,
                               CredentialCipher cipher,
                               ModelGateway gateway,
                               TransactionTemplate transactionTemplate) {
        this(mapper, cipher, gateway, transactionTemplate, DEFAULT_TEST_LIMITS);
    }

    AiConnectionService(AiConnectionMapper mapper,
                        CredentialCipher cipher,
                        ModelGateway gateway,
                        TransactionTemplate transactionTemplate,
                        ConnectionTestLimits testLimits) {
        this.mapper = mapper;
        this.cipher = cipher;
        this.gateway = gateway;
        this.transactionTemplate = transactionTemplate;
        this.testLimits = testLimits;
    }

    @Transactional(readOnly = true)
    public PageResult<ConnectionResponse> list(Boolean enabled, int page, int size) {
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 100);
        List<ConnectionResponse> items = mapper.findPage(enabled, (p - 1) * s, s)
                .stream().map(ConnectionResponse::from).toList();
        return new PageResult<>(items, p, s, mapper.countPage(enabled));
    }

    @Transactional(readOnly = true)
    public ConnectionResponse get(Long id) {
        return ConnectionResponse.from(require(id));
    }

    @Transactional
    public ConnectionResponse create(ConnectionRequest request) {
        if (request.apiKey() == null || request.apiKey().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "apiKey 不能为空");
        }
        AiConnectionRecord record = newRecord(request.providerType(), request.name(), request.baseUrl(),
                request.endpointPath(), request.protocol(), request.modelName(), request.apiKey(),
                request.timeoutMs(), request.enabled());
        mapper.insertConnection(record);
        mapper.insertModelProfile(record);
        return ConnectionResponse.from(require(record.getId()));
    }

    @Transactional
    public ConnectionResponse update(Long id, UpdateConnectionRequest request) {
        AiConnectionRecord old = require(id);
        if (request.expectedVersion() == null
                || old.getConfigurationVersion() != request.expectedVersion()) {
            throw versionConflict();
        }
        String normalizedProvider = request.providerType().trim().toUpperCase();
        String normalizedProtocol = normalizeProtocol(request.protocol());
        NormalizedAddress normalizedAddress = normalizeAddress(
                request.baseUrl(), request.endpointPath(), normalizedProtocol);
        String normalizedBaseUrl = normalizedAddress.baseUrl();
        String normalizedEndpointPath = normalizedAddress.endpointPath();
        boolean credentialChanged = request.apiKey() != null && !request.apiKey().isBlank();
        boolean connectionChanged = !old.getProviderType().equalsIgnoreCase(normalizedProvider)
                || !old.getBaseUrl().equals(normalizedBaseUrl)
                || !old.getEndpointPath().equals(normalizedEndpointPath)
                || !old.getProtocol().equalsIgnoreCase(normalizedProtocol)
                || !old.getModelName().equals(request.modelName().trim())
                || old.getTimeoutMs() != normalizedTimeout(request.timeoutMs())
                || credentialChanged;
        AiConnectionRecord record = newRecord(request.providerType(), request.name(), request.baseUrl(),
                request.endpointPath(), request.protocol(), request.modelName(),
                request.apiKey() == null || request.apiKey().isBlank()
                        ? null : request.apiKey(),
                request.timeoutMs(), request.enabled());
        record.setId(id);
        record.setModelProfileId(old.getModelProfileId());
        record.setEnabled(request.enabled() == null ? old.isEnabled() : request.enabled());
        record.setConfigurationVersion(request.expectedVersion());
        record.setLastTestStatus(connectionChanged ? "STALE" : old.getLastTestStatus());
        if (mapper.updateConnection(record) != 1) {
            throw versionConflict();
        }
        if (mapper.updateModelProfile(record) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "AI Connection 模型配置已变化，请刷新后重试");
        }
        return ConnectionResponse.from(require(id));
    }

    @Transactional
    public ConnectionResponse setEnabled(Long id, boolean enabled, long expectedVersion) {
        AiConnectionRecord current = require(id);
        if (current.getConfigurationVersion() != expectedVersion
                || mapper.updateEnabled(id, enabled, expectedVersion) != 1) {
            throw versionConflict();
        }
        return ConnectionResponse.from(require(id));
    }

    @Transactional
    public void delete(Long id) {
        AiConnectionRecord old = require(id);
        if (mapper.softDelete(old.getId(), old.getConfigurationVersion()) != 1) {
            throw versionConflict();
        }
    }

    @Transactional(readOnly = true)
    public AiConnectionRuntimeConfig runtimeConfig(Long id) {
        AiConnectionRecord record = require(id);
        if (!record.isEnabled()) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT, "AI Connection 未启用");
        }
        String key = cipher.decrypt(record.getCredentialCiphertext(), record.getCredentialIv(),
                record.getCredentialKeyVersion());
        return new AiConnectionRuntimeConfig(record.getId(), record.getModelProfileId(), record.getName(),
                record.getBaseUrl(), record.getEndpointPath(), record.getProtocol(),
                record.getModelName(), key, record.getTimeoutMs());
    }

    public AiConnectionRuntimeConfig getRuntimeConfig(Long id) {
        return runtimeConfig(id);
    }

    public ConnectionTestResponse testDraft(DraftTestRequest request) {
        AiConnectionRecord record = newRecord(request.providerType(), request.name(), request.baseUrl(),
                request.endpointPath(), request.protocol(), request.modelName(), request.apiKey(),
                request.timeoutMs(), true);
        return executeTest(record, request.streaming(), request.testPrompt(), request.apiKey());
    }

    public ConnectionTestResponse testSaved(Long id, SavedTestRequest request) {
        AiConnectionRecord record = require(id);
        String apiKey = cipher.decrypt(record.getCredentialCiphertext(),
                record.getCredentialIv(), record.getCredentialKeyVersion());
        ConnectionTestResponse result = executeTest(
                record, request.streaming(), request.testPrompt(), apiKey);
        transactionTemplate.executeWithoutResult(status -> {
            mapper.insertTestRecord(toTestRecord(record, result));
            mapper.updateTestSummary(id, record.getConfigurationVersion(),
                    result.status(), Instant.now(), result.latencyMs(),
                    result.errorCode(), result.errorSummary());
        });
        return result;
    }

    private ConnectionTestResponse executeTest(AiConnectionRecord record,
                                               boolean streaming,
                                               String prompt,
                                               String apiKey) {
        AiConnectionRuntimeConfig config = new AiConnectionRuntimeConfig(record.getId(),
                record.getModelProfileId(), record.getName(), record.getBaseUrl(), record.getEndpointPath(),
                record.getProtocol(), record.getModelName(), apiKey, record.getTimeoutMs());
        long start = System.nanoTime();
        ConnectionTestResponse result;
        try {
            ConnectionTestBudget budget = new ConnectionTestBudget(testLimits);
            Duration totalTimeout = connectionTestTimeout(record.getTimeoutMs());
            List<AiStreamEvent> events = gateway.stream(new AiInvocation(config,
                    "Reply briefly and do not expose secrets.", prompt == null || prompt.isBlank()
                            ? "Reply with OK only." : prompt, null, null, streaming,
                    CONNECTION_TEST_MAX_OUTPUT_TOKENS))
                    .doOnNext(budget::accept)
                    .collectList()
                    .timeout(totalTimeout)
                    .block(totalTimeout);
            if (events == null) events = List.of();
            boolean done = events.stream()
                    .anyMatch(event -> "run.completed".equals(event.type()) && event.done());
            boolean hasText = events.stream()
                    .anyMatch(event -> "output.text.delta".equals(event.type())
                            && event.text() != null && !event.text().isBlank());
            result = done && hasText
                    ? new ConnectionTestResponse("SUCCESS", record.getProtocol(), streaming,
                    elapsedMillis(start), 200, events.size(), true, null, null)
                    : failed(record, streaming, elapsedMillis(start),
                    done ? ErrorCode.INVALID_STRUCTURED_OUTPUT.name()
                            : ErrorCode.STREAM_INTERRUPTED.name(),
                    done ? "模型未返回非空文本内容" : "模型流未返回完整结束标记");
        } catch (AiProviderException exception) {
            result = failed(record, streaming, elapsedMillis(start),
                    exception.getHttpStatus(),
                    exception.getErrorCode().name(), exception.getMessage());
        } catch (ApiException exception) {
            result = failed(record, streaming, elapsedMillis(start),
                    exception.getErrorCode().name(), exception.getMessage());
        } catch (Exception exception) {
            boolean timedOut = isTimeout(exception);
            result = failed(record, streaming, elapsedMillis(start),
                    timedOut ? ErrorCode.REQUEST_TIMEOUT.name()
                            : ErrorCode.UPSTREAM_UNAVAILABLE.name(),
                    timedOut ? "模型请求超时" : "模型连接失败");
        }
        return result;
    }

    private ConnectionTestResponse failed(AiConnectionRecord record, boolean streaming, int latency,
                                          String errorCode, String summary) {
        return failed(record, streaming, latency, null, errorCode, summary);
    }

    private ConnectionTestResponse failed(AiConnectionRecord record,
                                          boolean streaming,
                                          int latency,
                                          Integer httpStatus,
                                          String errorCode,
                                          String summary) {
        return new ConnectionTestResponse("FAILED", record.getProtocol(), streaming, latency, httpStatus,
                0, false, errorCode, summary);
    }

    private ConnectionTestRecord toTestRecord(AiConnectionRecord connection, ConnectionTestResponse result) {
        ConnectionTestRecord record = new ConnectionTestRecord();
        record.setConnectionId(connection.getId());
        record.setProtocol(connection.getProtocol());
        record.setModelName(connection.getModelName());
        record.setStreaming(result.streaming());
        record.setStatus(result.status());
        record.setHttpStatus(result.httpStatus());
        record.setLatencyMs(result.latencyMs());
        record.setEventCount(result.eventCount());
        record.setDoneMarkerReceived(result.doneMarkerReceived());
        record.setErrorCode(result.errorCode());
        record.setErrorSummary(result.errorSummary());
        record.setConfigurationVersion(connection.getConfigurationVersion());
        record.setTestedAt(Instant.now());
        return record;
    }

    private int elapsedMillis(long start) {
        return Math.max(0, (int) ((System.nanoTime() - start) / 1_000_000L));
    }

    private AiConnectionRecord newRecord(String providerType, String name, String baseUrl, String endpointPath,
                                         String protocol, String modelName, String apiKey, Integer timeoutMs,
                                         Boolean enabled) {
        String provider = providerType.trim().toUpperCase();
        Long providerId = mapper.findProviderId(provider);
        if (providerId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "不支持的供应商类型");
        }
        String normalizedProtocol = normalizeProtocol(protocol);
        NormalizedAddress normalizedAddress = normalizeAddress(baseUrl, endpointPath, normalizedProtocol);
        CredentialCipher.EncryptedCredential encrypted = apiKey == null || apiKey.isBlank()
                ? null : cipher.encrypt(apiKey);
        Instant now = Instant.now();
        AiConnectionRecord record = new AiConnectionRecord();
        record.setProviderCatalogId(providerId);
        record.setProviderType(provider);
        record.setName(name.trim());
        record.setBaseUrl(normalizedAddress.baseUrl());
        record.setEndpointPath(normalizedAddress.endpointPath());
        record.setProtocol(normalizedProtocol);
        record.setModelName(modelName.trim());
        record.setModelDisplayName(modelName.trim());
        record.setSupportedProtocols("[\"" + record.getProtocol() + "\"]");
        record.setCapabilitiesJson("{\"supportsStreaming\":true}");
        record.setDefaultParametersJson("{}");
        if (encrypted != null) {
            record.setCredentialCiphertext(encrypted.ciphertext());
            record.setCredentialIv(encrypted.iv());
            record.setCredentialKeyVersion(encrypted.keyVersion());
            record.setCredentialMasked(encrypted.masked());
        }
        record.setEnabled(enabled == null || enabled);
        record.setTimeoutMs(timeoutMs == null ? 30000 : timeoutMs);
        record.setLastTestStatus("NOT_TESTED");
        record.setConfigurationVersion(1);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        return record;
    }

    private AiConnectionRecord require(Long id) {
        AiConnectionRecord record = mapper.findById(id);
        if (record == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "AI Connection 不存在");
        }
        return record;
    }

    private NormalizedAddress normalizeAddress(String rawBaseUrl,
                                               String rawEndpointPath,
                                               String protocol) {
        try {
            URI uri = URI.create(rawBaseUrl.trim());
            if (uri.getScheme() == null
                    || (!"http".equalsIgnoreCase(uri.getScheme())
                    && !"https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException();
            }
            String path = normalizePath(uri.getPath());
            String rawEndpoint = rawEndpointPath == null ? "" : rawEndpointPath.trim();
            String endpoint = rawEndpoint.isBlank() ? "" : normalizePath(rawEndpoint);
            if (rawEndpoint.startsWith("http://") || rawEndpoint.startsWith("https://")) {
                URI endpointUri = URI.create(rawEndpoint);
                if (!uri.getScheme().equalsIgnoreCase(endpointUri.getScheme())
                        || !uri.getHost().equalsIgnoreCase(endpointUri.getHost())
                        || uri.getPort() != endpointUri.getPort()) {
                    throw new IllegalArgumentException("endpoint origin");
                }
                String absoluteEndpoint = normalizePath(endpointUri.getPath());
                if (!path.isBlank() && absoluteEndpoint.startsWith(path + "/")) {
                    endpoint = absoluteEndpoint.substring(path.length());
                } else if (!path.isBlank() && absoluteEndpoint.equals(path)) {
                    endpoint = endpointSuffix(absoluteEndpoint, protocol);
                    path = path.substring(0, path.length() - endpoint.length());
                } else {
                    endpoint = absoluteEndpoint;
                }
            }
            if (endpoint.isBlank()) {
                endpoint = endpointSuffix(path, protocol);
            }
            if (path.endsWith(endpoint) && path.length() > endpoint.length()) {
                path = path.substring(0, path.length() - endpoint.length());
            }
            String normalizedBase = new URI(uri.getScheme().toLowerCase(), null,
                    uri.getHost(), uri.getPort(), path, null, null).toString();
            return new NormalizedAddress(normalizedBase, endpoint);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "baseUrl 必须是合法 HTTP(S) 地址");
        }
    }

    private String normalizePath(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String value = raw.trim();
        if (value.startsWith("http://") || value.startsWith("https://")) {
            URI endpoint = URI.create(value);
            if (endpoint.getHost() == null || endpoint.getQuery() != null
                    || endpoint.getFragment() != null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                        "endpointPath 必须是不带查询参数的路径");
            }
            value = endpoint.getPath();
        }
        if (value == null || value.isBlank() || "/".equals(value)) {
            return "";
        }
        if (value.contains("?") || value.contains("#")
                || value.contains(" ") || value.contains("\t")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "endpointPath 不能包含查询参数或空白字符");
        }
        value = value.replace('\\', '/');
        if (!value.startsWith("/")) value = "/" + value;
        while (value.contains("//")) value = value.replace("//", "/");
        while (value.length() > 1 && value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String endpointSuffix(String path, String protocol) {
        if (path.endsWith("/chat/completions")) {
            return "/chat/completions";
        }
        if (path.endsWith("/responses")) {
            return "/responses";
        }
        return "RESPONSES".equals(protocol) ? "/responses" : "/chat/completions";
    }

    private String normalizeProtocol(String raw) {
        String value = raw == null ? "" : raw.trim().toUpperCase();
        if (!"CHAT_COMPLETIONS".equals(value) && !"RESPONSES".equals(value)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "protocol 必须为 CHAT_COMPLETIONS 或 RESPONSES");
        }
        return value;
    }

    private int normalizedTimeout(Integer timeoutMs) {
        return timeoutMs == null ? 30000 : timeoutMs;
    }

    private ApiException versionConflict() {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                "AI Connection 配置已变化，请刷新后重试");
    }

    private Duration connectionTestTimeout(int timeoutMs) {
        Duration configured = Duration.ofMillis(Math.max(1L, timeoutMs))
                .plus(testLimits.timeoutGrace());
        return configured.compareTo(testLimits.absoluteTotalDuration()) <= 0
                ? configured
                : testLimits.absoluteTotalDuration();
    }

    private boolean isTimeout(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof TimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    record ConnectionTestLimits(int maxEvents,
                                int maxCharacters,
                                int maxBytes,
                                Duration absoluteTotalDuration,
                                Duration timeoutGrace) {
        ConnectionTestLimits {
            if (maxEvents <= 0 || maxCharacters <= 0 || maxBytes <= 0
                    || absoluteTotalDuration == null || absoluteTotalDuration.isZero()
                    || absoluteTotalDuration.isNegative()
                    || timeoutGrace == null || timeoutGrace.isNegative()) {
                throw new IllegalArgumentException("连接测试限制必须为正数");
            }
        }
    }

    private static final class ConnectionTestBudget {
        private final ConnectionTestLimits limits;
        private int eventCount;
        private int characterCount;
        private int byteCount;

        private ConnectionTestBudget(ConnectionTestLimits limits) {
            this.limits = limits;
        }

        private void accept(AiStreamEvent event) {
            eventCount++;
            if (eventCount > limits.maxEvents()) {
                throw limitExceeded("模型连接测试事件数超过安全上限");
            }
            acceptText(event.type());
            acceptText(event.text());
            acceptText(event.providerRequestId());
            acceptText(event.errorCode());
            acceptText(event.errorSummary());
        }

        private void acceptText(String value) {
            if (value == null || value.isEmpty()) {
                return;
            }
            characterCount += value.codePointCount(0, value.length());
            byteCount += value.getBytes(StandardCharsets.UTF_8).length;
            if (characterCount > limits.maxCharacters()) {
                throw limitExceeded("模型连接测试字符数超过安全上限");
            }
            if (byteCount > limits.maxBytes()) {
                throw limitExceeded("模型连接测试字节数超过安全上限");
            }
        }

        private ApiException limitExceeded(String message) {
            return new ApiException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    ErrorCode.OUTPUT_LIMIT_EXCEEDED,
                    message);
        }
    }

    private record NormalizedAddress(String baseUrl, String endpointPath) {
    }

    public record AiConnectionRuntimeConfig(Long connectionId, Long modelProfileId, String connectionName,
                                             String baseUrl, String endpointPath, String protocol,
                                             String modelName, String apiKey, int timeoutMs) {}
}
