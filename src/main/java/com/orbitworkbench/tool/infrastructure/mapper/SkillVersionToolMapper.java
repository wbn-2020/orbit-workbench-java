package com.orbitworkbench.tool.infrastructure.mapper;

import com.orbitworkbench.tool.domain.SkillVersionToolBindingRecord;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface SkillVersionToolMapper {

    List<Long> findToolVersionIds(@Param("skillVersionId") Long skillVersionId);

    void insert(SkillVersionToolBindingRecord binding);

    int deleteBySkillVersionId(@Param("skillVersionId") Long skillVersionId);
}
