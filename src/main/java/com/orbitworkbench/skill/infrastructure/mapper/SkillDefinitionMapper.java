package com.orbitworkbench.skill.infrastructure.mapper;

import com.orbitworkbench.skill.domain.SkillDefinitionRecord;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface SkillDefinitionMapper {

    List<SkillDefinitionRecord> findByWorkspaceId(@Param("workspaceId") Long workspaceId);

    SkillDefinitionRecord findById(@Param("id") Long id);

    SkillDefinitionRecord findByIdForUpdate(@Param("id") Long id);

    SkillDefinitionRecord findByWorkspaceCode(@Param("workspaceId") Long workspaceId,
                                              @Param("skillCode") String skillCode);

    void insert(SkillDefinitionRecord definition);

    int update(@Param("definition") SkillDefinitionRecord definition,
               @Param("expectedLockVersion") Long expectedLockVersion);

    int bumpLockVersion(@Param("id") Long id,
                        @Param("expectedLockVersion") Long expectedLockVersion,
                        @Param("now") Instant now);

    int setPublishedVersion(@Param("id") Long id,
                            @Param("versionId") Long versionId,
                            @Param("now") Instant now);

    int disable(@Param("id") Long id,
                @Param("now") Instant now);
}
