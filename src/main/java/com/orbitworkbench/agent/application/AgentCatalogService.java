package com.orbitworkbench.agent.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.agent.api.AgentCatalogDtos.AgentDefinitionResponse;
import com.orbitworkbench.agent.api.AgentCatalogDtos.PromptVersionResponse;
import com.orbitworkbench.agent.domain.AgentDefinitionDetailRecord;
import com.orbitworkbench.agent.domain.PromptVersionDetailRecord;
import com.orbitworkbench.agent.infrastructure.mapper.AgentCatalogMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentCatalogService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE =
            new TypeReference<>() {
            };
    private static final TypeReference<List<String>> STRING_LIST_TYPE =
            new TypeReference<>() {
            };

    private final AgentCatalogMapper mapper;
    private final ObjectMapper objectMapper;

    public AgentCatalogService(AgentCatalogMapper mapper,
                               ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<AgentDefinitionResponse> definitions() {
        return mapper.findDefinitions().stream()
                .map(record -> toDefinition(record, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public AgentDefinitionResponse definition(Long id) {
        AgentDefinitionDetailRecord record = mapper.findDefinition(id);
        if (record == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    ErrorCode.RESOURCE_NOT_FOUND,
                    "AgentDefinition 不存在");
        }
        return toDefinition(record, true);
    }

    @Transactional(readOnly = true)
    public List<PromptVersionResponse> promptVersions(Long templateId) {
        if (mapper.findPromptTemplateId(templateId) == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    ErrorCode.RESOURCE_NOT_FOUND,
                    "PromptTemplate 不存在");
        }
        return mapper.findPromptVersions(templateId).stream()
                .map(this::toPromptVersion)
                .toList();
    }

    private AgentDefinitionResponse toDefinition(
            AgentDefinitionDetailRecord record,
            boolean includePrompt) {
        Map<String, Object> configuration = readMap(record.getConfigurationJson());
        return new AgentDefinitionResponse(
                record.getId(),
                record.getName(),
                record.getDescription(),
                record.getStatus(),
                record.getDefaultConnectionId(),
                record.getDefaultModelProfileId(),
                record.getPromptVersionId(),
                record.getPromptTemplateId(),
                record.getPromptTemplateName(),
                record.getPromptVersionNumber(),
                includePrompt ? record.getPromptContent() : null,
                readList(record.getPromptVariablesJson()),
                configuration,
                configuration.get("moduleType") == null
                        ? null : configuration.get("moduleType").toString(),
                record.getCreatedAt(),
                record.getUpdatedAt());
    }

    private PromptVersionResponse toPromptVersion(
            PromptVersionDetailRecord record) {
        return new PromptVersionResponse(
                record.getId(),
                record.getTemplateId(),
                record.getTemplateName(),
                record.getPurpose(),
                record.getVersionNumber(),
                record.getContent(),
                readList(record.getVariablesJson()),
                record.getCreatedAt());
    }

    private Map<String, Object> readMap(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    private List<String> readList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            List<String> values = objectMapper.readValue(value, STRING_LIST_TYPE);
            return values == null ? List.of() : List.copyOf(new ArrayList<>(values));
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }
}
