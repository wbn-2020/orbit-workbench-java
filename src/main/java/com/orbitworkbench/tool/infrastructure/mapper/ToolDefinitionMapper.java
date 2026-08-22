package com.orbitworkbench.tool.infrastructure.mapper;

import com.orbitworkbench.tool.domain.ToolDefinitionRecord;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ToolDefinitionMapper {

    ToolDefinitionRecord findEnabledLatestByCode(@Param("toolCode") String toolCode);

    ToolDefinitionRecord findEnabledByCodeAndVersion(@Param("toolCode") String toolCode,
                                                     @Param("toolVersion") Integer toolVersion);

    ToolDefinitionRecord findById(@Param("id") Long id);

    List<ToolDefinitionRecord> findAllEnabled();
}
