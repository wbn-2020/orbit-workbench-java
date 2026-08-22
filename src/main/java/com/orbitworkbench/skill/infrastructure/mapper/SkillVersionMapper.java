package com.orbitworkbench.skill.infrastructure.mapper;

import com.orbitworkbench.skill.domain.SkillVersionRecord;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface SkillVersionMapper {

    void insert(SkillVersionRecord version);

    SkillVersionRecord findById(@Param("id") Long id);

    SkillVersionRecord findByIdForUpdate(@Param("id") Long id);

    SkillVersionRecord findDraftForUpdate(@Param("skillDefinitionId") Long skillDefinitionId);

    List<SkillVersionRecord> findBySkillDefinitionId(
            @Param("skillDefinitionId") Long skillDefinitionId);

    int updateDraft(SkillVersionRecord version);

    int publishDraft(@Param("skillDefinitionId") Long skillDefinitionId,
                     @Param("versionId") Long versionId,
                     @Param("now") Instant now);

    int disableAll(@Param("skillDefinitionId") Long skillDefinitionId,
                   @Param("now") Instant now);

    int disableDrafts(@Param("skillDefinitionId") Long skillDefinitionId,
                      @Param("now") Instant now);

    int nextVersionNumber(@Param("skillDefinitionId") Long skillDefinitionId);
}
