package com.orbitworkbench.mcp.infrastructure.mapper;

import com.orbitworkbench.mcp.domain.McpServerRecord;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface McpServerMapper {

    List<McpServerRecord> findByWorkspaceId(@Param("workspaceId") Long workspaceId);

    McpServerRecord findById(@Param("id") Long id);

    McpServerRecord findByIdForUpdate(@Param("id") Long id);

    void insert(McpServerRecord server);

    int update(@Param("server") McpServerRecord server,
               @Param("expectedLockVersion") Long expectedLockVersion);

    int updateStatus(@Param("id") Long id,
                     @Param("expectedStatus") String expectedStatus,
                     @Param("status") String status,
                     @Param("now") Instant now);

    int markSyncSuccess(@Param("id") Long id,
                        @Param("now") Instant now);

    int markSyncFailure(@Param("id") Long id,
                        @Param("errorCode") String errorCode,
                        @Param("errorSummary") String errorSummary,
                        @Param("now") Instant now);
}
