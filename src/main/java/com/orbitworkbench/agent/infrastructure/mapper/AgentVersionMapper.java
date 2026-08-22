package com.orbitworkbench.agent.infrastructure.mapper;

import com.orbitworkbench.agent.domain.AgentVersionRecord;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface AgentVersionMapper {

    void insert(AgentVersionRecord version);

    AgentVersionRecord findById(@Param("id") Long id);

    AgentVersionRecord findByIdForUpdate(@Param("id") Long id);

    AgentVersionRecord findPublishedForUpdate(@Param("agentDefinitionId") Long agentDefinitionId);

    AgentVersionRecord findPublishedByModuleType(
            @Param("moduleType") String moduleType,
            @Param("workspaceId") Long workspaceId);

    AgentVersionRecord findPublishedByModuleTypeForUpdate(
            @Param("moduleType") String moduleType,
            @Param("workspaceId") Long workspaceId);

    AgentVersionRecord findDraftForUpdate(@Param("agentDefinitionId") Long agentDefinitionId);

    List<AgentVersionRecord> findByAgentDefinitionId(
            @Param("agentDefinitionId") Long agentDefinitionId);

    int updateDraft(AgentVersionRecord version);

    int publishDraft(@Param("agentDefinitionId") Long agentDefinitionId,
                     @Param("versionId") Long versionId,
                     @Param("now") java.time.Instant now);

    int disableAgent(@Param("agentDefinitionId") Long agentDefinitionId,
                     @Param("now") java.time.Instant now);

    int disableDrafts(@Param("agentDefinitionId") Long agentDefinitionId,
                      @Param("now") java.time.Instant now);

    int nextVersionNumber(@Param("agentDefinitionId") Long agentDefinitionId);

    Long findActiveConnectionId(@Param("id") Long id);

    Long findEnabledModelProfileId(@Param("id") Long id);

    Long findModelProfileConnectionId(@Param("id") Long id);

    Long findPromptVersionId(@Param("id") Long id);

    Long findPromptTemplateId(@Param("id") Long id);

    String findPromptContent(@Param("id") Long id);
}
