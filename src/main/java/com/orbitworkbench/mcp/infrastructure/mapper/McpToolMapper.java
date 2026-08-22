package com.orbitworkbench.mcp.infrastructure.mapper;

import com.orbitworkbench.mcp.domain.McpToolRecord;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface McpToolMapper {

    List<McpToolRecord> findByServerId(@Param("serverId") Long serverId);

    McpToolRecord findById(@Param("id") Long id);

    McpToolRecord findByToolCode(@Param("toolCode") String toolCode);

    McpToolRecord findByServerAndName(@Param("serverId") Long serverId,
                                      @Param("toolName") String toolName);

    void insert(McpToolRecord tool);

    int update(McpToolRecord tool);

    int updateEnabled(@Param("id") Long id,
                      @Param("enabled") boolean enabled,
                      @Param("now") Instant now);

    int disableExceptNames(@Param("serverId") Long serverId,
                           @Param("toolNames") List<String> toolNames,
                           @Param("now") Instant now);
}
