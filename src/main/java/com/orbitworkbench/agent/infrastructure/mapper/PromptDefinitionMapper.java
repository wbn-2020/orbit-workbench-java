package com.orbitworkbench.agent.infrastructure.mapper;

import com.orbitworkbench.agent.domain.PromptTemplateRecord;
import com.orbitworkbench.agent.domain.PromptVersionRecord;
import org.apache.ibatis.annotations.Param;

public interface PromptDefinitionMapper {

    void insertTemplate(PromptTemplateRecord template);

    void insertVersion(PromptVersionRecord version);

    int nextVersionNumber(@Param("templateId") Long templateId);

    Long findVersionId(@Param("id") Long id);
}
