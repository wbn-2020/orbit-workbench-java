package com.orbitworkbench.tool.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.tool.api.ToolCatalogDtos.ToolCommandRequest;
import com.orbitworkbench.tool.api.ToolCatalogDtos.ToolCatalogResponse;
import com.orbitworkbench.tool.api.ToolCatalogDtos.ToolVersionRequest;
import com.orbitworkbench.tool.api.ToolCatalogDtos.ToolVersionResponse;
import com.orbitworkbench.tool.domain.ToolCatalogRecord;
import com.orbitworkbench.tool.domain.ToolVersionRecord;
import com.orbitworkbench.tool.infrastructure.mapper.ToolCatalogMapper;
import com.orbitworkbench.tool.infrastructure.mapper.ToolVersionMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ToolCatalogService {

    private static final Set<String> STATUSES = Set.of("DRAFT", "PUBLISHED", "DISABLED");

    private final ToolCatalogMapper catalogMapper;
    private final ToolVersionMapper versionMapper;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    public ToolCatalogService(ToolCatalogMapper catalogMapper,
                              ToolVersionMapper versionMapper,
                              ToolRegistry toolRegistry,
                              ObjectMapper objectMapper) {
        this.catalogMapper = catalogMapper;
        this.versionMapper = versionMapper;
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<ToolCatalogResponse> list(String status) {
        String normalizedStatus = normalizeStatus(status);
        return catalogMapper.findAll(normalizedStatus).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ToolCatalogResponse get(Long id) {
        return toResponse(require(id));
    }

    @Transactional
    public ToolCatalogResponse enable(Long id) {
        ToolCatalogRecord catalog = requireForUpdate(id);
        ToolVersionRecord published = catalog.getPublishedVersionId() == null
                ? null : versionMapper.findById(catalog.getPublishedVersionId());
        if (published == null
                || !"PUBLISHED".equals(published.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    ErrorCode.TOOL_VERSION_INVALID,
                    "工具没有可启用的已发布版本");
        }
        if (!"PUBLISHED".equals(catalog.getStatus())
                && catalogMapper.updateStatus(
                        id, catalog.getStatus(), "PUBLISHED", Instant.now()) != 1) {
            throw new ApiException(HttpStatus.CONFLICT,
                    ErrorCode.TOOL_PUBLISH_CONFLICT,
                    "工具状态已变化，请刷新后重试");
        }
        return get(id);
    }

    @Transactional
    public ToolCatalogResponse disable(Long id) {
        ToolCatalogRecord catalog = requireForUpdate(id);
        Instant now = Instant.now();
        if (!"DISABLED".equals(catalog.getStatus())
                && catalogMapper.updateStatus(
                        id, catalog.getStatus(), "DISABLED", now) != 1) {
            throw new ApiException(HttpStatus.CONFLICT,
                    ErrorCode.TOOL_PUBLISH_CONFLICT,
                    "工具状态已变化，请刷新后重试");
        }
        return get(id);
    }

    @Transactional
    public ToolCatalogResponse createVersion(Long id, ToolVersionRequest request) {
        ToolCatalogRecord catalog = requireForUpdate(id);
        requireActive(catalog);
        checkLockVersion(catalog, request.expectedVersion());
        validateRequest(catalog, request);

        ToolVersionRecord draft = versionMapper.findDraftForUpdate(id);
        Instant now = Instant.now();
        if (draft == null) {
            draft = newVersion(id, versionMapper.nextVersionNumber(id), request, now);
            versionMapper.insert(draft);
        } else {
            applyRequest(draft, request, now);
            if (versionMapper.updateDraft(draft) != 1) {
                throw new ApiException(HttpStatus.CONFLICT,
                        ErrorCode.TOOL_VERSION_INVALID,
                        "Tool 草稿状态已变化，请刷新后重试");
            }
        }
        if (catalogMapper.bumpLockVersion(id, catalog.getLockVersion(), now) != 1) {
            throw versionConflict();
        }
        return get(id);
    }

    @Transactional
    public ToolCatalogResponse publish(Long id, ToolCommandRequest request) {
        ToolCatalogRecord catalog = requireForUpdate(id);
        requireActive(catalog);
        long expectedVersion = request == null || request.expectedVersion() == null
                ? catalog.getLockVersion() : request.expectedVersion();
        checkLockVersion(catalog, expectedVersion);
        ToolVersionRecord version = request != null && request.versionId() != null
                ? requireVersionForUpdate(id, request.versionId())
                : versionMapper.findDraftForUpdate(id);
        if (version == null || !"DRAFT".equals(version.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    ErrorCode.TOOL_VERSION_INVALID,
                    "Tool 没有可发布的草稿版本");
        }
        validateStoredVersion(catalog, version);
        Instant now = Instant.now();
        if (versionMapper.publishDraft(id, version.getId(), now) != 1
                || catalogMapper.setPublishedVersion(
                        id, version.getId(), expectedVersion, now) != 1) {
            throw versionConflict();
        }
        return get(id);
    }

    private ToolCatalogRecord require(Long id) {
        ToolCatalogRecord catalog = catalogMapper.findById(id);
        if (catalog == null) {
            throw new ApiException(HttpStatus.NOT_FOUND,
                    ErrorCode.TOOL_NOT_FOUND,
                    "工具目录不存在");
        }
        return catalog;
    }

    private ToolCatalogRecord requireForUpdate(Long id) {
        ToolCatalogRecord catalog = catalogMapper.findByIdForUpdate(id);
        if (catalog == null) {
            throw new ApiException(HttpStatus.NOT_FOUND,
                    ErrorCode.TOOL_NOT_FOUND,
                    "工具目录不存在");
        }
        return catalog;
    }

    private ToolVersionRecord requireVersionForUpdate(Long catalogId, Long versionId) {
        ToolVersionRecord version = versionMapper.findByIdForUpdate(versionId);
        if (version == null || !catalogId.equals(version.getToolCatalogId())) {
            throw new ApiException(HttpStatus.NOT_FOUND,
                    ErrorCode.TOOL_VERSION_INVALID,
                    "Tool 版本不存在");
        }
        return version;
    }

    private ToolVersionRecord newVersion(Long catalogId,
                                         int versionNumber,
                                         ToolVersionRequest request,
                                         Instant now) {
        ToolVersionRecord version = new ToolVersionRecord();
        version.setToolCatalogId(catalogId);
        version.setVersionNumber(versionNumber);
        version.setStatus("DRAFT");
        applyRequest(version, request, now);
        version.setCreatedAt(now);
        return version;
    }

    private void applyRequest(ToolVersionRecord version,
                              ToolVersionRequest request,
                              Instant now) {
        version.setInputSchemaJson(writeJson(request.inputSchema()));
        version.setOutputSchemaJson(writeJson(request.outputSchema()));
        version.setRiskLevel(normalizeRiskLevel(request.riskLevel()));
        version.setRequiresConfirmation(request.requiresConfirmation());
        version.setTimeoutMs(request.timeoutMs());
        version.setMaxResultBytes(request.maxResultBytes());
        version.setCapabilitiesJson(writeJson(request.capabilities()));
        version.setUpdatedAt(now);
    }

    private void validateRequest(ToolCatalogRecord catalog, ToolVersionRequest request) {
        if (!toolRegistry.hasHandler(catalog.getToolCode())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    ErrorCode.TOOL_DISABLED,
                    "工具处理器未注册");
        }
        normalizeRiskLevel(request.riskLevel());
        if (request.timeoutMs() == null || request.timeoutMs() < 1
                || request.timeoutMs() > 120000
                || request.maxResultBytes() == null || request.maxResultBytes() < 1
                || request.maxResultBytes() > 1048576) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION_FAILED,
                    "Tool 版本执行限制不合法");
        }
    }

    private void validateStoredVersion(ToolCatalogRecord catalog,
                                       ToolVersionRecord version) {
        if (!toolRegistry.hasHandler(catalog.getToolCode())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    ErrorCode.TOOL_DISABLED,
                    "工具处理器未注册");
        }
        normalizeRiskLevel(version.getRiskLevel());
        if (version.getTimeoutMs() == null || version.getTimeoutMs() < 1
                || version.getTimeoutMs() > 120000
                || version.getMaxResultBytes() == null || version.getMaxResultBytes() < 1
                || version.getMaxResultBytes() > 1048576) {
            throw new ApiException(HttpStatus.CONFLICT,
                    ErrorCode.TOOL_VERSION_INVALID,
                    "Tool 版本执行限制不合法");
        }
        readJson(version.getInputSchemaJson());
        readJson(version.getOutputSchemaJson());
        readJson(version.getCapabilitiesJson());
    }

    private void requireActive(ToolCatalogRecord catalog) {
        if ("DISABLED".equals(catalog.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    ErrorCode.TOOL_PUBLISH_CONFLICT,
                    "工具已停用");
        }
    }

    private void checkLockVersion(ToolCatalogRecord catalog, Long expectedVersion) {
        if (expectedVersion != null
                && !expectedVersion.equals(catalog.getLockVersion())) {
            throw versionConflict();
        }
    }

    private ApiException versionConflict() {
        return new ApiException(HttpStatus.CONFLICT,
                ErrorCode.TOOL_PUBLISH_CONFLICT,
                "Tool 已被其他请求修改，请刷新后重试");
    }

    private String normalizeRiskLevel(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        if (!Set.of("LOW", "MEDIUM", "HIGH").contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION_FAILED,
                    "riskLevel 必须为 LOW、MEDIUM 或 HIGH");
        }
        return normalized;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    ErrorCode.TOOL_VERSION_INVALID,
                    "Tool JSON 配置无法序列化");
        }
    }

    private ToolCatalogResponse toResponse(ToolCatalogRecord catalog) {
        List<ToolVersionResponse> versions = versionMapper
                .findByCatalogId(catalog.getId()).stream()
                .map(this::toVersionResponse)
                .toList();
        return new ToolCatalogResponse(
                catalog.getId(),
                catalog.getToolCode(),
                catalog.getName(),
                catalog.getDescription(),
                catalog.getHandlerType(),
                catalog.getPublishedVersionId(),
                catalog.getStatus(),
                catalog.getLockVersion(),
                versions,
                catalog.getCreatedAt(),
                catalog.getUpdatedAt());
    }

    private ToolVersionResponse toVersionResponse(ToolVersionRecord version) {
        return new ToolVersionResponse(
                version.getId(),
                version.getToolCatalogId(),
                version.getVersionNumber(),
                version.getStatus(),
                readJson(version.getInputSchemaJson()),
                readJson(version.getOutputSchemaJson()),
                version.getRiskLevel(),
                version.isRequiresConfirmation(),
                version.getTimeoutMs(),
                version.getMaxResultBytes(),
                readJson(version.getCapabilitiesJson()),
                version.getPublishedAt(),
                version.getCreatedAt(),
                version.getUpdatedAt());
    }

    private Object readJson(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    ErrorCode.TOOL_VERSION_INVALID,
                    "工具版本 JSON 配置无法读取");
        }
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toUpperCase();
        if (!STATUSES.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION_FAILED,
                    "工具状态不受支持");
        }
        return normalized;
    }
}
