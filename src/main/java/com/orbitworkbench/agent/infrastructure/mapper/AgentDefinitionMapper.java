package com.orbitworkbench.agent.infrastructure.mapper;

import com.orbitworkbench.agent.domain.AgentDefinitionDetailRecord;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface AgentDefinitionMapper {

    Long findActiveIdByModuleType(@Param("moduleType") String moduleType);

    String findPromptContent(@Param("id") Long id);

    List<AgentDefinitionDetailRecord> findAgents(@Param("workspaceId") Long workspaceId);

    AgentDefinitionDetailRecord findAgent(@Param("id") Long id);

    AgentDefinitionDetailRecord findAgentForUpdate(@Param("id") Long id);

    void insertAgent(AgentDefinitionDetailRecord agent);

    int updateAgent(AgentDefinitionDetailRecord agent);

    int updateLegacyConfiguration(AgentDefinitionDetailRecord agent);

    int bumpVersion(@Param("id") Long id,
                    @Param("expectedVersion") Long expectedVersion,
                    @Param("now") java.time.Instant now);

    int publishAgent(@Param("id") Long id,
                     @Param("publishedVersionId") Long publishedVersionId,
                     @Param("expectedVersion") Long expectedVersion,
                     @Param("now") java.time.Instant now);

    int disableAgent(@Param("id") Long id,
                     @Param("expectedVersion") Long expectedVersion,
                     @Param("now") java.time.Instant now);
}
