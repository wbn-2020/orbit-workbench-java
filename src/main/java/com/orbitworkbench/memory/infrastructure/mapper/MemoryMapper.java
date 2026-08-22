package com.orbitworkbench.memory.infrastructure.mapper;

import com.orbitworkbench.memory.domain.MemoryRecord;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface MemoryMapper {

    List<MemoryRecord> findPage(@Param("workspaceId") Long workspaceId,
                                @Param("status") String status,
                                @Param("memoryType") String memoryType,
                                @Param("query") String query,
                                @Param("offset") long offset,
                                @Param("limit") int limit);

    long countPage(@Param("workspaceId") Long workspaceId,
                   @Param("status") String status,
                   @Param("memoryType") String memoryType,
                   @Param("query") String query);

    List<MemoryRecord> findForInjection(@Param("workspaceId") Long workspaceId,
                                        @Param("now") Instant now,
                                        @Param("limit") int limit);

    MemoryRecord findById(@Param("id") Long id);

    MemoryRecord findByIdForUpdate(@Param("id") Long id);

    void insert(MemoryRecord record);

    int update(@Param("record") MemoryRecord record,
               @Param("expectedVersion") Long expectedVersion);

    int updateStatus(@Param("id") Long id,
                     @Param("expectedStatus") String expectedStatus,
                     @Param("status") String status,
                     @Param("expectedVersion") Long expectedVersion,
                     @Param("now") Instant now);
}
