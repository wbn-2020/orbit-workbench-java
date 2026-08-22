package com.orbitworkbench.agent.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.agent.api.AgentDtos.AgentCommandRequest;
import com.orbitworkbench.agent.api.AgentDtos.AgentRequest;
import com.orbitworkbench.agent.api.AgentDtos.AgentResponse;
import com.orbitworkbench.agent.api.AgentDtos.AgentVersionRequest;
import com.orbitworkbench.agent.api.AgentDtos.AgentVersionResponse;
import com.orbitworkbench.agent.domain.AgentDefinitionDetailRecord;
import com.orbitworkbench.agent.domain.AgentVersionRecord;
import com.orbitworkbench.agent.infrastructure.mapper.AgentDefinitionMapper;
import com.orbitworkbench.agent.infrastructure.mapper.AgentVersionMapper;
import com.orbitworkbench.agent.domain.PromptTemplateRecord;
import com.orbitworkbench.agent.domain.PromptVersionRecord;
import com.orbitworkbench.agent.infrastructure.mapper.PromptDefinitionMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.skill.domain.SkillDefinitionRecord;
import com.orbitworkbench.skill.domain.SkillVersionRecord;
import com.orbitworkbench.skill.infrastructure.mapper.SkillDefinitionMapper;
import com.orbitworkbench.skill.infrastructure.mapper.SkillVersionMapper;
import com.orbitworkbench.tool.domain.ToolVersionRecord;
import com.orbitworkbench.tool.infrastructure.mapper.SkillVersionToolMapper;
import com.orbitworkbench.tool.infrastructure.mapper.ToolVersionMapper;
import com.orbitworkbench.workspace.application.WorkspaceService;
import java.util.ArrayList;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentService {

    private static final TypeReference<Map<String, Object>> CONFIGURATION_TYPE =
            new TypeReference<>() {
            };
    private static final Set<String> MODULE_TYPES =
            Set.of("TECH_LEARNING", "DATA_ANALYSIS");

    private final AgentDefinitionMapper definitionMapper;
    private final AgentVersionMapper versionMapper;
    private final PromptDefinitionMapper promptMapper;
    private final SkillDefinitionMapper skillDefinitionMapper;
    private final SkillVersionMapper skillVersionMapper;
    private final SkillVersionToolMapper skillVersionToolMapper;
    private final ToolVersionMapper toolVersionMapper;
    private final WorkspaceService workspaceService;
    private final ObjectMapper objectMapper;

    public AgentService(AgentDefinitionMapper definitionMapper,
                        AgentVersionMapper versionMapper,
                        PromptDefinitionMapper promptMapper,
                        SkillDefinitionMapper skillDefinitionMapper,
                        SkillVersionMapper skillVersionMapper,
                        SkillVersionToolMapper skillVersionToolMapper,
                        ToolVersionMapper toolVersionMapper,
                        WorkspaceService workspaceService,
                        ObjectMapper objectMapper) {
        this.definitionMapper = definitionMapper;
        this.versionMapper = versionMapper;
        this.promptMapper = promptMapper;
        this.skillDefinitionMapper = skillDefinitionMapper;
        this.skillVersionMapper = skillVersionMapper;
        this.skillVersionToolMapper = skillVersionToolMapper;
        this.toolVersionMapper = toolVersionMapper;
        this.workspaceService = workspaceService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<AgentResponse> list(Long workspaceId) {
        if (workspaceId != null) {
            workspaceService.require(workspaceId);
        }
        return definitionMapper.findAgents(workspaceId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AgentResponse get(Long id) {
        return toResponse(requireAgent(id));
    }

    @Transactional
    public AgentResponse create(AgentRequest request) {
        workspaceService.require(request.workspaceId());
        String code = requiredText(request.code(), "code", 128);
        String name = requiredText(request.name(), "name", 128);
        String description = optionalText(request.description(), 512);
        Instant now = Instant.now();
        Map<String, Object> configuration = request.configuration() == null
                ? Map.of() : new LinkedHashMap<>(request.configuration());
        String configurationJson = writeConfiguration(configuration);
        Long promptVersionId = resolvePromptVersion(
                request.name(),
                request.promptVersionId(),
                request.promptContent(),
                request.promptVariables(),
                null,
                now);
        validateReferences(request.connectionId(), request.modelProfileId(),
                promptVersionId, false);

        AgentDefinitionDetailRecord agent = new AgentDefinitionDetailRecord();
        agent.setWorkspaceId(request.workspaceId());
        agent.setCode(code);
        agent.setName(name);
        agent.setDescription(description);
        agent.setStatus("ACTIVE");
        agent.setDefaultConnectionId(request.connectionId());
        agent.setDefaultModelProfileId(request.modelProfileId());
        agent.setPromptVersionId(promptVersionId);
        agent.setConfigurationJson(configurationJson);
        agent.setVersion(1L);
        agent.setCreatedAt(now);
        agent.setUpdatedAt(now);
        try {
            definitionMapper.insertAgent(agent);
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "当前工作空间已经存在相同 Agent code");
        }

        AgentVersionRecord draft = newVersion(
                agent.getId(),
                1,
                request.connectionId(),
                request.modelProfileId(),
                promptVersionId,
                configurationJson,
                now);
        versionMapper.insert(draft);
        return get(agent.getId());
    }

    @Transactional
    public AgentResponse update(Long id, AgentRequest request) {
        AgentDefinitionDetailRecord current = requireAgentForUpdate(id);
        if (!Objects.equals(current.getWorkspaceId(), request.workspaceId())) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "Agent 不属于请求的工作空间");
        }
        if (!Objects.equals(current.getVersion(), request.expectedVersion())) {
            throw versionConflict();
        }
        ensureActive(current);

        AgentVersionRecord draft = versionMapper.findDraftForUpdate(id);
        AgentVersionRecord source = draft == null
                ? versionMapper.findPublishedForUpdate(id) : draft;
        Instant now = Instant.now();
        AgentVersionRecord edited = draft == null
                ? copyAsDraft(source, id, versionMapper.nextVersionNumber(id), now)
                : draft;
        applyVersionRequest(edited, request.connectionId(), request.modelProfileId(),
                request.promptVersionId(), request.promptContent(), request.promptVariables(),
                request.configuration(), source, current.getName(), now);
        validateReferences(edited.getConnectionId(), edited.getModelProfileId(),
                edited.getPromptVersionId(), false);

        current.setCode(requiredText(request.code(), "code", 128));
        current.setName(requiredText(request.name(), "name", 128));
        current.setDescription(optionalText(request.description(), 512));
        current.setUpdatedAt(now);
        try {
            if (definitionMapper.updateAgent(current) != 1) {
                throw versionConflict();
            }
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "当前工作空间已经存在相同 Agent code");
        }
        persistDraft(edited, draft != null);
        syncLegacyConfiguration(current, edited);
        return get(id);
    }

    @Transactional
    public AgentResponse createVersion(Long id, AgentVersionRequest request) {
        AgentDefinitionDetailRecord agent = requireAgentForUpdate(id);
        ensureActive(agent);
        if (request.expectedVersion() != null
                && !Objects.equals(agent.getVersion(), request.expectedVersion())) {
            throw versionConflict();
        }
        AgentVersionRecord source = versionMapper.findDraftForUpdate(id);
        if (source == null) {
            source = versionMapper.findPublishedForUpdate(id);
        }
        Instant now = Instant.now();
        AgentVersionRecord draft = copyAsDraft(
                source, id, versionMapper.nextVersionNumber(id), now);
        applyVersionRequest(draft, request.connectionId(), request.modelProfileId(),
                request.promptVersionId(), request.promptContent(), request.promptVariables(),
                request.configuration(), source, agent.getName(), now);
        validateReferences(draft.getConnectionId(), draft.getModelProfileId(),
                draft.getPromptVersionId(), false);
        versionMapper.disableDrafts(id, now);
        versionMapper.insert(draft);
        bumpAgentVersion(agent);
        syncLegacyConfiguration(agent, draft);
        return get(id);
    }

    @Transactional
    public AgentResponse publish(Long id, AgentCommandRequest request) {
        AgentDefinitionDetailRecord agent = requireAgentForUpdate(id);
        ensureActive(agent);
        long expectedVersion = request == null || request.expectedVersion() == null
                ? agent.getVersion() : request.expectedVersion();
        if (agent.getVersion() != expectedVersion) {
            throw publishConflict("Agent 已被其他请求修改，请刷新后重试");
        }

        AgentVersionRecord draft = request != null && request.versionId() != null
                ? versionMapper.findByIdForUpdate(request.versionId())
                : versionMapper.findDraftForUpdate(id);
        if (draft == null
                || !Objects.equals(draft.getAgentDefinitionId(), id)
                || !"DRAFT".equals(draft.getStatus())) {
            throw publishConflict("只能发布当前 Agent 的草稿版本");
        }
        validateReferences(draft.getConnectionId(), draft.getModelProfileId(),
                draft.getPromptVersionId(), true);
        validateConfiguration(draft.getConfigurationJson(), agent.getWorkspaceId());

        Instant now = Instant.now();
        if (versionMapper.publishDraft(id, draft.getId(), now) != 1
                || definitionMapper.publishAgent(id, draft.getId(), expectedVersion, now) != 1) {
            throw publishConflict("Agent 发布状态已变化，请刷新后重试");
        }
        return get(id);
    }

    @Transactional
    public AgentResponse disable(Long id, AgentCommandRequest request) {
        AgentDefinitionDetailRecord agent = requireAgentForUpdate(id);
        long expectedVersion = request == null || request.expectedVersion() == null
                ? agent.getVersion() : request.expectedVersion();
        if (agent.getVersion() != expectedVersion) {
            throw versionConflict();
        }
        Instant now = Instant.now();
        versionMapper.disableAgent(id, now);
        if (definitionMapper.disableAgent(id, expectedVersion, now) != 1) {
            throw versionConflict();
        }
        return get(id);
    }

    private AgentDefinitionDetailRecord requireAgent(Long id) {
        AgentDefinitionDetailRecord agent = definitionMapper.findAgent(id);
        if (agent == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "Agent 不存在");
        }
        return agent;
    }

    private AgentDefinitionDetailRecord requireAgentForUpdate(Long id) {
        AgentDefinitionDetailRecord agent = definitionMapper.findAgentForUpdate(id);
        if (agent == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "Agent 不存在");
        }
        return agent;
    }

    private void ensureActive(AgentDefinitionDetailRecord agent) {
        if (!"ACTIVE".equals(agent.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "已停用的 Agent 不能修改或发布");
        }
    }

    private void persistDraft(AgentVersionRecord draft, boolean existing) {
        if (existing) {
            if (versionMapper.updateDraft(draft) != 1) {
                throw new ApiException(HttpStatus.CONFLICT, ErrorCode.AGENT_VERSION_INVALID,
                        "草稿版本已被其他请求修改");
            }
        } else {
            versionMapper.insert(draft);
        }
    }

    private void applyVersionRequest(AgentVersionRecord target,
                                     Long connectionId,
                                     Long modelProfileId,
                                     Long promptVersionId,
                                     String promptContent,
                                     Object promptVariables,
                                     Map<String, Object> configuration,
                                     AgentVersionRecord source,
                                     String agentName,
                                     Instant now) {
        if (connectionId != null) {
            target.setConnectionId(connectionId);
        } else if (source == null) {
            target.setConnectionId(null);
        }
        if (modelProfileId != null) {
            target.setModelProfileId(modelProfileId);
        } else if (source == null) {
            target.setModelProfileId(null);
        }
        Long resolvedPromptVersionId = resolvePromptVersion(
                agentName,
                promptVersionId,
                promptContent,
                promptVariables,
                source == null ? null : source.getPromptVersionId(),
                now);
        target.setPromptVersionId(resolvedPromptVersionId);
        if (configuration != null) {
            target.setConfigurationJson(writeConfiguration(new LinkedHashMap<>(configuration)));
        } else if (source == null) {
            target.setConfigurationJson("{}");
        }
        target.setUpdatedAt(now);
    }

    private Long resolvePromptVersion(String agentName,
                                      Long promptVersionId,
                                      String promptContent,
                                      Object promptVariables,
                                      Long sourcePromptVersionId,
                                      Instant now) {
        if (promptVersionId != null) {
            return promptVersionId;
        }
        if (promptContent == null || promptContent.isBlank()) {
            return sourcePromptVersionId;
        }
        String content = requiredText(promptContent, "promptContent", 20000);
        Long templateId = sourcePromptVersionId == null
                ? null : versionMapper.findPromptTemplateId(sourcePromptVersionId);
        if (templateId == null) {
            PromptTemplateRecord template = new PromptTemplateRecord();
            template.setName("Agent: " + requiredText(agentName, "name", 128));
            template.setPurpose("Orbit Workbench Agent 系统 Prompt");
            template.setCreatedAt(now);
            template.setUpdatedAt(now);
            promptMapper.insertTemplate(template);
            templateId = template.getId();
        }
        PromptVersionRecord version = new PromptVersionRecord();
        version.setTemplateId(templateId);
        version.setVersionNumber(promptMapper.nextVersionNumber(templateId));
        version.setContent(content);
        version.setVariablesJson(writePromptVariables(promptVariables));
        version.setCreatedAt(now);
        promptMapper.insertVersion(version);
        return version.getId();
    }

    private String writePromptVariables(Object variables) {
        if (variables == null) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(variables);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "promptVariables 必须是有效 JSON");
        }
    }

    private void validateReferences(Long connectionId,
                                    Long modelProfileId,
                                    Long promptVersionId,
                                    boolean publishing) {
        if (connectionId != null && versionMapper.findActiveConnectionId(connectionId) == null) {
            throw invalidVersion("AI Connection 不存在、未启用或已删除");
        }
        if (modelProfileId != null) {
            Long modelConnectionId = versionMapper.findModelProfileConnectionId(modelProfileId);
            if (modelConnectionId == null
                    || (connectionId != null && !connectionId.equals(modelConnectionId))) {
                throw invalidVersion("Model Profile 不存在、未启用或与 Connection 不匹配");
            }
        }
        if (promptVersionId != null
                && (versionMapper.findPromptVersionId(promptVersionId) == null
                || isBlank(versionMapper.findPromptContent(promptVersionId)))) {
            throw invalidVersion("PromptVersion 不存在或内容为空");
        }
        if (publishing && (connectionId == null || modelProfileId == null || promptVersionId == null)) {
            throw invalidVersion("发布版本必须绑定 Connection、Model Profile 和 PromptVersion");
        }
    }

    private void validateConfiguration(String configurationJson, Long workspaceId) {
        Map<String, Object> configuration = readConfiguration(configurationJson);
        Object moduleType = configuration.get("moduleType");
        if (moduleType == null
                || !MODULE_TYPES.contains(moduleType.toString().trim().toUpperCase())) {
            throw invalidVersion("configuration.moduleType 必须为 TECH_LEARNING 或 DATA_ANALYSIS");
        }
        Map<String, Long> boundTools = new LinkedHashMap<>();
        for (Long toolVersionId : readVersionIds(configuration.get("toolVersionIds"),
                "configuration.toolVersionIds")) {
            validatePublishedTool(toolVersionId, boundTools);
        }
        for (Long skillVersionId : readVersionIds(configuration.get("skillVersionIds"),
                "configuration.skillVersionIds")) {
            SkillVersionRecord skillVersion = skillVersionMapper.findById(skillVersionId);
            if (skillVersion == null || !"PUBLISHED".equals(skillVersion.getStatus())) {
                throw invalidVersion("configuration.skillVersionIds 只能引用已发布 SkillVersion");
            }
            SkillDefinitionRecord skill = skillDefinitionMapper
                    .findById(skillVersion.getSkillDefinitionId());
            if (skill == null || workspaceId == null
                    || !workspaceId.equals(skill.getWorkspaceId())
                    || !"PUBLISHED".equals(skill.getStatus())) {
                throw invalidVersion("SkillVersion 不存在、未发布或不属于当前 Workspace");
            }
            for (Long toolVersionId : skillVersionToolMapper
                    .findToolVersionIds(skillVersionId)) {
                validatePublishedTool(toolVersionId, boundTools);
            }
        }
    }

    private void validatePublishedTool(Long toolVersionId,
                                       Map<String, Long> boundTools) {
        ToolVersionRecord toolVersion = toolVersionMapper.findById(toolVersionId);
        if (toolVersion == null
                || !"PUBLISHED".equals(toolVersion.getStatus())
                || !"PUBLISHED".equals(toolVersion.getCatalogStatus())) {
            throw invalidVersion("ToolVersion 不存在、未发布或未启用");
        }
        Long existingVersionId = boundTools.putIfAbsent(
                toolVersion.getToolCode(), toolVersion.getId());
        if (existingVersionId != null
                && !existingVersionId.equals(toolVersion.getId())) {
            throw invalidVersion(
                    "同一个 toolCode 不能绑定多个不同 ToolVersion: "
                            + toolVersion.getToolCode());
        }
    }

    private List<Long> readVersionIds(Object value, String field) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> values)) {
            throw invalidVersion(field + " 必须是正整数数组");
        }
        List<Long> ids = new ArrayList<>();
        Set<Long> uniqueIds = new LinkedHashSet<>();
        for (Object item : values) {
            Long id = positiveId(item);
            if (id == null || !uniqueIds.add(id)) {
                throw invalidVersion(field + " 必须是无重复的正整数数组");
            }
            ids.add(id);
        }
        return ids;
    }

    private Long positiveId(Object value) {
        if (value instanceof Number number) {
            long id = number.longValue();
            return id > 0 && number.doubleValue() == id ? id : null;
        }
        if (value instanceof String text) {
            try {
                long id = Long.parseLong(text.trim());
                return id > 0 ? id : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private void syncLegacyConfiguration(AgentDefinitionDetailRecord agent,
                                         AgentVersionRecord version) {
        agent.setDefaultConnectionId(version.getConnectionId());
        agent.setDefaultModelProfileId(version.getModelProfileId());
        agent.setPromptVersionId(version.getPromptVersionId());
        agent.setConfigurationJson(version.getConfigurationJson());
        definitionMapper.updateLegacyConfiguration(agent);
    }

    private void bumpAgentVersion(AgentDefinitionDetailRecord agent) {
        Instant now = Instant.now();
        if (definitionMapper.bumpVersion(agent.getId(), agent.getVersion(), now) != 1) {
            throw versionConflict();
        }
        agent.setVersion(agent.getVersion() + 1);
        agent.setUpdatedAt(now);
    }

    private AgentVersionRecord copyAsDraft(AgentVersionRecord source,
                                           Long agentDefinitionId,
                                           int versionNumber,
                                           Instant now) {
        AgentVersionRecord draft = new AgentVersionRecord();
        draft.setAgentDefinitionId(agentDefinitionId);
        draft.setVersionNumber(versionNumber);
        draft.setStatus("DRAFT");
        if (source != null) {
            draft.setConnectionId(source.getConnectionId());
            draft.setModelProfileId(source.getModelProfileId());
            draft.setPromptVersionId(source.getPromptVersionId());
            draft.setConfigurationJson(source.getConfigurationJson());
        } else {
            draft.setConfigurationJson("{}");
        }
        draft.setCreatedAt(now);
        draft.setUpdatedAt(now);
        return draft;
    }

    private AgentVersionRecord newVersion(Long agentDefinitionId,
                                          int versionNumber,
                                          Long connectionId,
                                          Long modelProfileId,
                                          Long promptVersionId,
                                          String configurationJson,
                                          Instant now) {
        AgentVersionRecord version = new AgentVersionRecord();
        version.setAgentDefinitionId(agentDefinitionId);
        version.setVersionNumber(versionNumber);
        version.setStatus("DRAFT");
        version.setConnectionId(connectionId);
        version.setModelProfileId(modelProfileId);
        version.setPromptVersionId(promptVersionId);
        version.setConfigurationJson(configurationJson);
        version.setCreatedAt(now);
        version.setUpdatedAt(now);
        return version;
    }

    private AgentResponse toResponse(AgentDefinitionDetailRecord agent) {
        List<AgentVersionResponse> versions = versionMapper
                .findByAgentDefinitionId(agent.getId()).stream()
                .map(this::toVersionResponse)
                .toList();
        Long draftVersionId = versions.stream()
                .filter(version -> "DRAFT".equals(version.status()))
                .map(AgentVersionResponse::id)
                .findFirst()
                .orElse(null);
        return new AgentResponse(
                agent.getId(),
                agent.getWorkspaceId(),
                agent.getCode(),
                agent.getName(),
                agent.getDescription(),
                agent.getStatus(),
                agent.getPublishedVersionId(),
                agent.getVersion(),
                draftVersionId,
                versions,
                agent.getCreatedAt(),
                agent.getUpdatedAt());
    }

    private AgentVersionResponse toVersionResponse(AgentVersionRecord version) {
        return new AgentVersionResponse(
                version.getId(),
                version.getAgentDefinitionId(),
                version.getVersionNumber(),
                version.getStatus(),
                version.getConnectionId(),
                version.getConnectionName(),
                version.getModelProfileId(),
                version.getModelName(),
                version.getPromptVersionId(),
                version.getPromptVersionNumber(),
                version.getPromptContent(),
                readPromptVariables(version.getPromptVariablesJson()),
                readConfiguration(version.getConfigurationJson()),
                version.getPublishedAt(),
                version.getCreatedAt(),
                version.getUpdatedAt());
    }

    private Map<String, Object> readConfiguration(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return new LinkedHashMap<>(objectMapper.readValue(value, CONFIGURATION_TYPE));
        } catch (JsonProcessingException exception) {
            throw invalidVersion("configuration_json 不是有效 JSON 对象");
        }
    }

    private Object readPromptVariables(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, Object.class);
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private String writeConfiguration(Map<String, Object> configuration) {
        try {
            return objectMapper.writeValueAsString(configuration);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "configuration 必须是有效 JSON 对象");
        }
    }

    private ApiException invalidVersion(String message) {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.AGENT_VERSION_INVALID, message);
    }

    private ApiException publishConflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.AGENT_PUBLISH_CONFLICT, message);
    }

    private ApiException versionConflict() {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                "Agent 状态已变化，请刷新后重试");
    }

    private String requiredText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    field + " 不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    field + " 长度不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }

    private String optionalText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "description 长度不能超过 " + maxLength + " 个字符");
        }
        return normalized;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
