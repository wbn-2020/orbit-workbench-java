package com.orbitworkbench.tool.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.shared.api.PageResult;
import com.orbitworkbench.tool.api.ToolDtos.ToolCallResponse;
import com.orbitworkbench.tool.api.ToolDtos.ToolDefinitionResponse;
import com.orbitworkbench.tool.domain.ToolCallRecord;
import com.orbitworkbench.tool.domain.ToolDefinitionRecord;
import com.orbitworkbench.tool.infrastructure.mapper.ToolCallMapper;
import com.orbitworkbench.tool.infrastructure.mapper.ToolDefinitionMapper;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ToolQueryService {

    private final ToolDefinitionMapper definitionMapper;
    private final ToolCallMapper callMapper;
    private final ObjectMapper objectMapper;

    public ToolQueryService(ToolDefinitionMapper definitionMapper,
                            ToolCallMapper callMapper,
                            ObjectMapper objectMapper) {
        this.definitionMapper = definitionMapper;
        this.callMapper = callMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<ToolDefinitionResponse> definitions() {
        return definitionMapper.findAllEnabled().stream()
                .map(this::toDefinition)
                .toList();
    }

    @Transactional(readOnly = true)
    public ToolDefinitionResponse definition(Long id) {
        ToolDefinitionRecord definition = definitionMapper.findById(id);
        if (definition == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.TOOL_NOT_FOUND,
                    "工具定义不存在");
        }
        return toDefinition(definition);
    }

    @Transactional(readOnly = true)
    public PageResult<ToolCallResponse> calls(Long runId, int page, int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        long offset = ((long) normalizedPage - 1) * normalizedSize;
        List<ToolCallResponse> items = callMapper.findByRunId(
                        runId, offset, normalizedSize)
                .stream()
                .map(this::toCall)
                .toList();
        return new PageResult<>(
                items,
                normalizedPage,
                normalizedSize,
                callMapper.countByRunId(runId));
    }

    @Transactional(readOnly = true)
    public ToolCallResponse call(Long id) {
        ToolCallRecord call = callMapper.findById(id);
        if (call == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "工具调用不存在");
        }
        return toCall(call);
    }

    private ToolDefinitionResponse toDefinition(ToolDefinitionRecord definition) {
        return new ToolDefinitionResponse(
                definition.getId(),
                definition.getToolCode(),
                definition.getName(),
                definition.getDescription(),
                definition.getToolVersion(),
                readJson(definition.getInputSchemaJson()),
                readJson(definition.getOutputSchemaJson()),
                definition.getRiskLevel(),
                definition.isRequiresConfirmation(),
                definition.getTimeoutMs(),
                definition.getMaxResultBytes(),
                definition.isEnabled(),
                definition.getUpdatedAt());
    }

    private ToolCallResponse toCall(ToolCallRecord call) {
        return new ToolCallResponse(
                call.getId(),
                call.getAgentRunId(),
                call.getStepId(),
                call.getModelCallId(),
                call.getToolCode(),
                call.getToolVersion(),
                call.getToolVersionId(),
                call.getStatus(),
                call.getArgumentsSummary(),
                call.getResultSummary(),
                call.getResultSizeBytes(),
                ToolCallResponse.duration(call.getStartedAt(), call.getFinishedAt()),
                call.getErrorCode(),
                call.getErrorSummary(),
                call.getStartedAt(),
                call.getFinishedAt(),
                call.getCreatedAt());
    }

    private Object readJson(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            return java.util.Map.of();
        }
    }
}
