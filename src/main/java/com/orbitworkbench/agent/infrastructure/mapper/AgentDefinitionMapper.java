package com.orbitworkbench.agent.infrastructure.mapper;

import org.apache.ibatis.annotations.Param;

public interface AgentDefinitionMapper {

    Long findActiveIdByModuleType(@Param("moduleType") String moduleType);

    String findPromptContent(@Param("id") Long id);
}
