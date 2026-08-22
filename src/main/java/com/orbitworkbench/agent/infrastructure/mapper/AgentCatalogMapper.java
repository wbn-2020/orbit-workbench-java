package com.orbitworkbench.agent.infrastructure.mapper;

import com.orbitworkbench.agent.domain.AgentDefinitionDetailRecord;
import com.orbitworkbench.agent.domain.PromptVersionDetailRecord;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface AgentCatalogMapper {

    List<AgentDefinitionDetailRecord> findDefinitions();

    AgentDefinitionDetailRecord findDefinition(@Param("id") Long id);

    List<PromptVersionDetailRecord> findPromptVersions(
            @Param("templateId") Long templateId);

    Long findPromptTemplateId(@Param("templateId") Long templateId);
}
