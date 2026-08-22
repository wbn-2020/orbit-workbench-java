package com.orbitworkbench.skill.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.agent.infrastructure.mapper.PromptDefinitionMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.skill.api.SkillDtos.SkillCommandRequest;
import com.orbitworkbench.skill.api.SkillDtos.SkillRequest;
import com.orbitworkbench.skill.api.SkillDtos.SkillResponse;
import com.orbitworkbench.skill.api.SkillDtos.SkillVersionRequest;
import com.orbitworkbench.skill.api.SkillDtos.SkillVersionResponse;
import com.orbitworkbench.skill.domain.SkillDefinitionRecord;
import com.orbitworkbench.skill.domain.SkillVersionRecord;
import com.orbitworkbench.skill.infrastructure.mapper.SkillDefinitionMapper;
import com.orbitworkbench.skill.infrastructure.mapper.SkillVersionMapper;
import com.orbitworkbench.tool.domain.SkillVersionToolBindingRecord;
import com.orbitworkbench.tool.domain.ToolVersionRecord;
import com.orbitworkbench.tool.infrastructure.mapper.SkillVersionToolMapper;
import com.orbitworkbench.tool.infrastructure.mapper.ToolVersionMapper;
import com.orbitworkbench.workspace.application.WorkspaceService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SkillService {

    private final SkillDefinitionMapper definitionMapper;
    private final SkillVersionMapper versionMapper;
    private final SkillVersionToolMapper bindingMapper;
    private final ToolVersionMapper toolVersionMapper;
    private final PromptDefinitionMapper promptMapper;
    private final WorkspaceService workspaceService;
    private final ObjectMapper objectMapper;

    public SkillService(SkillDefinitionMapper definitionMapper,
                        SkillVersionMapper versionMapper,
                        SkillVersionToolMapper bindingMapper,
                        ToolVersionMapper toolVersionMapper,
                        PromptDefinitionMapper promptMapper,
                        WorkspaceService workspaceService,
                        ObjectMapper objectMapper) {
        this.definitionMapper = definitionMapper;
        this.versionMapper = versionMapper;
        this.bindingMapper = bindingMapper;
        this.toolVersionMapper = toolVersionMapper;
        this.promptMapper = promptMapper;
        this.workspaceService = workspaceService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<SkillResponse> list(Long workspaceId) {
        workspaceService.require(workspaceId);
        return definitionMapper.findByWorkspaceId(workspaceId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SkillResponse get(Long id) {
        return toResponse(require(id));
    }

    @Transactional
    public SkillResponse create(SkillRequest request) {
        workspaceService.require(request.workspaceId());
        String code = requiredText(request.skillCode(), "skillCode", 128);
        String name = requiredText(request.name(), "name", 128);
        Instant now = Instant.now();
        validateVersionRequest(request.version());

        SkillDefinitionRecord definition = new SkillDefinitionRecord();
        definition.setWorkspaceId(request.workspaceId());
        definition.setSkillCode(code);
        definition.setName(name);
        definition.setDescription(optionalText(request.description(), 512));
        definition.setStatus("DRAFT");
        definition.setLockVersion(1L);
        definition.setCreatedAt(now);
        definition.setUpdatedAt(now);
        try {
            definitionMapper.insert(definition);
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT,
                    ErrorCode.SKILL_PUBLISH_CONFLICT,
                    "当前工作空间已经存在相同 Skill code");
        }

        SkillVersionRecord version = newVersion(
                definition.getId(), 1, request.version(), now);
        versionMapper.insert(version);
        replaceBindings(version.getId(), request.version().toolVersionIds(), now);
        return get(definition.getId());
    }

    @Transactional
    public SkillResponse update(Long id, SkillRequest request) {
        SkillDefinitionRecord current = requireForUpdate(id);
        if (!Objects.equals(current.getWorkspaceId(), request.workspaceId())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    ErrorCode.SKILL_PUBLISH_CONFLICT,
                    "Skill 不属于请求的工作空间");
        }
        if (request.expectedVersion() != null
                && !Objects.equals(current.getLockVersion(), request.expectedVersion())) {
            throw versionConflict();
        }
        requireActive(current);
        validateVersionRequest(request.version());

        SkillVersionRecord draft = versionMapper.findDraftForUpdate(id);
        SkillVersionRecord source = draft == null
                ? findPublished(id) : draft;
        Instant now = Instant.now();
        SkillVersionRecord edited = draft == null
                ? copyAsDraft(source, id, versionMapper.nextVersionNumber(id), now)
                : draft;
        applyVersionRequest(edited, request.version(), now);

        current.setSkillCode(requiredText(request.skillCode(), "skillCode", 128));
        current.setName(requiredText(request.name(), "name", 128));
        current.setDescription(optionalText(request.description(), 512));
        current.setUpdatedAt(now);
        try {
            if (definitionMapper.update(current, current.getLockVersion()) != 1) {
                throw versionConflict();
            }
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT,
                    ErrorCode.SKILL_PUBLISH_CONFLICT,
                    "当前工作空间已经存在相同 Skill code");
        }
        persistVersion(edited, draft != null, request.version().toolVersionIds(), now);
        return get(id);
    }

    @Transactional
    public SkillResponse createVersion(Long id, SkillVersionRequest request) {
        SkillDefinitionRecord definition = requireForUpdate(id);
        requireActive(definition);
        if (request.expectedVersion() != null
                && !Objects.equals(definition.getLockVersion(), request.expectedVersion())) {
            throw versionConflict();
        }
        validateVersionRequest(request);
        SkillVersionRecord draft = versionMapper.findDraftForUpdate(id);
        SkillVersionRecord source = draft == null ? findPublished(id) : draft;
        Instant now = Instant.now();
        SkillVersionRecord target = draft == null
                ? copyAsDraft(source, id, versionMapper.nextVersionNumber(id), now)
                : draft;
        applyVersionRequest(target, request, now);
        persistVersion(target, draft != null, request.toolVersionIds(), now);
        if (definitionMapper.bumpLockVersion(
                id, definition.getLockVersion(), now) != 1) {
            throw versionConflict();
        }
        return get(id);
    }

    @Transactional
    public SkillResponse publish(Long id, SkillCommandRequest request) {
        SkillDefinitionRecord definition = requireForUpdate(id);
        requireActive(definition);
        if (request != null
                && request.expectedVersion() != null
                && !Objects.equals(definition.getLockVersion(), request.expectedVersion())) {
            throw versionConflict();
        }
        SkillVersionRecord version = request == null || request.versionId() == null
                ? versionMapper.findDraftForUpdate(id)
                : requireVersionForUpdate(id, request.versionId());
        if (version == null || !"DRAFT".equals(version.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    ErrorCode.SKILL_VERSION_INVALID,
                    "Skill 没有可发布的草稿版本");
        }
        validateVersionRecord(version);
        Instant now = Instant.now();
        if (versionMapper.publishDraft(id, version.getId(), now) != 1) {
            throw new ApiException(HttpStatus.CONFLICT,
                    ErrorCode.SKILL_PUBLISH_CONFLICT,
                    "Skill 发布状态已变化，请刷新后重试");
        }
        if (definitionMapper.setPublishedVersion(id, version.getId(), now) != 1) {
            throw new ApiException(HttpStatus.CONFLICT,
                    ErrorCode.SKILL_PUBLISH_CONFLICT,
                    "Skill 发布指针更新失败，请刷新后重试");
        }
        return get(id);
    }

    @Transactional
    public SkillResponse disable(Long id) {
        SkillDefinitionRecord definition = requireForUpdate(id);
        Instant now = Instant.now();
        versionMapper.disableAll(id, now);
        if (!"DISABLED".equals(definition.getStatus())
                && definitionMapper.disable(id, now) != 1) {
            throw new ApiException(HttpStatus.CONFLICT,
                    ErrorCode.SKILL_PUBLISH_CONFLICT,
                    "Skill 状态已变化，请刷新后重试");
        }
        return get(id);
    }

    private SkillDefinitionRecord require(Long id) {
        SkillDefinitionRecord definition = definitionMapper.findById(id);
        if (definition == null) {
            throw new ApiException(HttpStatus.NOT_FOUND,
                    ErrorCode.SKILL_NOT_FOUND,
                    "Skill 不存在");
        }
        return definition;
    }

    private SkillDefinitionRecord requireForUpdate(Long id) {
        SkillDefinitionRecord definition = definitionMapper.findByIdForUpdate(id);
        if (definition == null) {
            throw new ApiException(HttpStatus.NOT_FOUND,
                    ErrorCode.SKILL_NOT_FOUND,
                    "Skill 不存在");
        }
        return definition;
    }

    private SkillVersionRecord requireVersionForUpdate(Long skillId, Long versionId) {
        SkillVersionRecord version = versionMapper.findByIdForUpdate(versionId);
        if (version == null || !skillId.equals(version.getSkillDefinitionId())) {
            throw new ApiException(HttpStatus.NOT_FOUND,
                    ErrorCode.SKILL_VERSION_INVALID,
                    "Skill 版本不存在");
        }
        return version;
    }

    private SkillVersionRecord findPublished(Long skillId) {
        SkillVersionRecord version = versionMapper.findBySkillDefinitionId(skillId).stream()
                .filter(candidate -> "PUBLISHED".equals(candidate.getStatus()))
                .findFirst()
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT,
                        ErrorCode.SKILL_VERSION_INVALID,
                        "Skill 没有可编辑的已发布版本"));
        return version;
    }

    private SkillVersionRecord newVersion(Long skillId,
                                          int versionNumber,
                                          SkillVersionRequest request,
                                          Instant now) {
        SkillVersionRecord version = new SkillVersionRecord();
        version.setSkillDefinitionId(skillId);
        version.setVersionNumber(versionNumber);
        version.setStatus("DRAFT");
        applyVersionRequest(version, request, now);
        version.setCreatedAt(now);
        version.setUpdatedAt(now);
        return version;
    }

    private SkillVersionRecord copyAsDraft(SkillVersionRecord source,
                                           Long skillId,
                                           int versionNumber,
                                           Instant now) {
        if (source == null) {
            throw new ApiException(HttpStatus.CONFLICT,
                    ErrorCode.SKILL_VERSION_INVALID,
                    "Skill 没有可编辑的版本");
        }
        SkillVersionRecord draft = new SkillVersionRecord();
        draft.setSkillDefinitionId(skillId);
        draft.setVersionNumber(versionNumber);
        draft.setStatus("DRAFT");
        draft.setPromptVersionId(source.getPromptVersionId());
        draft.setInputSchemaJson(source.getInputSchemaJson());
        draft.setOutputType(source.getOutputType());
        draft.setRuntimeLimitsJson(source.getRuntimeLimitsJson());
        draft.setCreatedAt(now);
        draft.setUpdatedAt(now);
        draft.setToolVersionIds(bindingMapper.findToolVersionIds(source.getId()));
        return draft;
    }

    private void applyVersionRequest(SkillVersionRecord target,
                                     SkillVersionRequest request,
                                     Instant now) {
        target.setPromptVersionId(request.promptVersionId());
        target.setInputSchemaJson(writeJson(
                request.inputSchema() == null ? Map.of() : request.inputSchema()));
        target.setOutputType(requiredText(request.outputType(), "outputType", 64));
        target.setRuntimeLimitsJson(writeJson(
                request.runtimeLimits() == null ? Map.of() : request.runtimeLimits()));
        target.setUpdatedAt(now);
    }

    private void persistVersion(SkillVersionRecord version,
                                boolean existingDraft,
                                List<Long> toolVersionIds,
                                Instant now) {
        if (existingDraft) {
            if (versionMapper.updateDraft(version) != 1) {
                throw new ApiException(HttpStatus.CONFLICT,
                        ErrorCode.SKILL_VERSION_INVALID,
                        "Skill 草稿状态已变化，请刷新后重试");
            }
        } else {
            versionMapper.insert(version);
        }
        replaceBindings(version.getId(), toolVersionIds, now);
    }

    private void replaceBindings(Long skillVersionId,
                                 List<Long> toolVersionIds,
                                 Instant now) {
        bindingMapper.deleteBySkillVersionId(skillVersionId);
        List<Long> uniqueIds = new ArrayList<>(
                new LinkedHashSet<>(toolVersionIds == null ? List.of() : toolVersionIds));
        for (int index = 0; index < uniqueIds.size(); index++) {
            SkillVersionToolBindingRecord binding = new SkillVersionToolBindingRecord();
            binding.setSkillVersionId(skillVersionId);
            binding.setToolVersionId(uniqueIds.get(index));
            binding.setBindingOrder(index + 1);
            binding.setCreatedAt(now);
            bindingMapper.insert(binding);
        }
    }

    private void validateVersionRequest(SkillVersionRequest request) {
        if (promptMapper.findVersionId(request.promptVersionId()) == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    ErrorCode.SKILL_VERSION_INVALID,
                    "Skill 引用的 PromptVersion 不存在");
        }
        List<Long> ids = request.toolVersionIds() == null
                ? List.of() : request.toolVersionIds();
        Set<Long> uniqueIds = new LinkedHashSet<>(ids);
        if (uniqueIds.size() != ids.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    ErrorCode.SKILL_TOOL_INVALID,
                    "Skill 不能重复绑定同一个 ToolVersion");
        }
        for (Long id : uniqueIds) {
            ToolVersionRecord tool = toolVersionMapper.findById(id);
            if (tool == null
                    || !"PUBLISHED".equals(tool.getStatus())
                    || !"PUBLISHED".equals(tool.getCatalogStatus())) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        ErrorCode.SKILL_TOOL_INVALID,
                        "Skill 只能绑定已发布且已启用的 ToolVersion");
            }
        }
    }

    private void validateVersionRecord(SkillVersionRecord version) {
        validateVersionRequest(new SkillVersionRequest(
                version.getPromptVersionId(),
                readMap(version.getInputSchemaJson()),
                version.getOutputType(),
                readMap(version.getRuntimeLimitsJson()),
                bindingMapper.findToolVersionIds(version.getId()),
                null));
    }

    private SkillResponse toResponse(SkillDefinitionRecord definition) {
        List<SkillVersionResponse> versions = versionMapper
                .findBySkillDefinitionId(definition.getId()).stream()
                .map(version -> {
                    version.setToolVersionIds(bindingMapper.findToolVersionIds(version.getId()));
                    return toVersionResponse(version);
                })
                .toList();
        return new SkillResponse(
                definition.getId(),
                definition.getWorkspaceId(),
                definition.getSkillCode(),
                definition.getName(),
                definition.getDescription(),
                definition.getPublishedVersionId(),
                definition.getStatus(),
                definition.getLockVersion(),
                versions,
                definition.getCreatedAt(),
                definition.getUpdatedAt());
    }

    private SkillVersionResponse toVersionResponse(SkillVersionRecord version) {
        return new SkillVersionResponse(
                version.getId(),
                version.getSkillDefinitionId(),
                version.getVersionNumber(),
                version.getStatus(),
                version.getPromptVersionId(),
                version.getPromptVersionNumber(),
                readJson(version.getInputSchemaJson()),
                version.getOutputType(),
                readJson(version.getRuntimeLimitsJson()),
                version.getToolVersionIds(),
                version.getPublishedAt(),
                version.getCreatedAt(),
                version.getUpdatedAt());
    }

    private Map<String, Object> readMap(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value,
                    new com.fasterxml.jackson.core.type.TypeReference<>() {
                    });
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    ErrorCode.SKILL_VERSION_INVALID,
                    "Skill JSON 配置无法读取");
        }
    }

    private Object readJson(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    ErrorCode.SKILL_VERSION_INVALID,
                    "Skill JSON 配置无法读取");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    ErrorCode.SKILL_VERSION_INVALID,
                    "Skill JSON 配置无法序列化");
        }
    }

    private void requireActive(SkillDefinitionRecord definition) {
        if ("DISABLED".equals(definition.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    ErrorCode.SKILL_PUBLISH_CONFLICT,
                    "Skill 已停用");
        }
    }

    private ApiException versionConflict() {
        return new ApiException(HttpStatus.CONFLICT,
                ErrorCode.SKILL_PUBLISH_CONFLICT,
                "Skill 已被其他请求修改，请刷新后重试");
    }

    private String requiredText(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION_FAILED,
                    field + " 不合法");
        }
        return value.trim();
    }

    private String optionalText(String value, int maxLength) {
        return value == null || value.isBlank()
                ? null : requiredText(value, "description", maxLength);
    }
}
