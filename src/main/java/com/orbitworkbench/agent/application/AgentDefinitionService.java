package com.orbitworkbench.agent.application;

import com.orbitworkbench.agent.infrastructure.mapper.AgentDefinitionMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentDefinitionService {

    private final AgentDefinitionMapper mapper;

    public AgentDefinitionService(AgentDefinitionMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public Long requireActiveId(String moduleType) {
        Long id = mapper.findActiveIdByModuleType(moduleType);
        if (id == null) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "当前任务类型没有可用的 AgentDefinition");
        }
        return id;
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
}
