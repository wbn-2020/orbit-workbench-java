package com.orbitworkbench.agent.application;

import com.orbitworkbench.agent.domain.AgentVersionRecord;
import com.orbitworkbench.agent.infrastructure.mapper.AgentDefinitionMapper;
import com.orbitworkbench.agent.infrastructure.mapper.AgentVersionMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentDefinitionService {

    private final AgentDefinitionMapper mapper;
    private final AgentVersionMapper versionMapper;

    public AgentDefinitionService(AgentDefinitionMapper mapper,
                                  AgentVersionMapper versionMapper) {
        this.mapper = mapper;
        this.versionMapper = versionMapper;
    }

    @Transactional(readOnly = true)
    public Long requireActiveId(String moduleType) {
        AgentVersionRecord version = versionMapper.findPublishedByModuleType(
                moduleType, null);
        if (version == null) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "当前任务类型没有可用的 AgentDefinition");
        }
        return version.getAgentDefinitionId();
    }

    @Transactional(readOnly = true)
    public String requirePrompt(Long id) {
        if (id == null) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "运行缺少 AgentDefinition");
        }
        String prompt = mapper.findPromptContent(id);
        if (prompt == null || prompt.isBlank()) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "AgentDefinition 缺少可用 Prompt");
        }
        return prompt;
    }

    @Transactional(readOnly = true)
    public String requirePrompt(Long versionId, Long definitionId) {
        if (versionId != null) {
            AgentVersionRecord version = versionMapper.findById(versionId);
            if (version == null || !Objects.equals(definitionId, version.getAgentDefinitionId())
                    || !List.of("PUBLISHED", "DISABLED").contains(version.getStatus())
                    || version.getPromptContent() == null
                    || version.getPromptContent().isBlank()) {
                throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                        "AgentVersion 缺少可用 Prompt");
            }
            return version.getPromptContent();
        }
        return requirePrompt(definitionId);
    }

    @Transactional
    public AgentVersionRecord requirePublishedVersion(String moduleType, Long workspaceId) {
        AgentVersionRecord version = versionMapper.findPublishedByModuleTypeForUpdate(
                moduleType, workspaceId);
        if (version == null) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "当前任务类型没有可用的已发布 AgentVersion");
        }
        return version;
    }
}
