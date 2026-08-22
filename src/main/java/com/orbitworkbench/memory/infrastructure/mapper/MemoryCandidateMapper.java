package com.orbitworkbench.memory.infrastructure.mapper;

import com.orbitworkbench.memory.domain.MemoryCandidateRecord;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface MemoryCandidateMapper {

    List<MemoryCandidateRecord> findPage(@Param("workspaceId") Long workspaceId,
                                         @Param("status") String status,
                                         @Param("offset") long offset,
                                         @Param("limit") int limit);

    long countPage(@Param("workspaceId") Long workspaceId,
                   @Param("status") String status);

    MemoryCandidateRecord findByIdForUpdate(@Param("id") Long id);

    void insert(MemoryCandidateRecord candidate);

    int updateStatus(@Param("id") Long id,
                     @Param("expectedStatus") String expectedStatus,
                     @Param("status") String status,
                     @Param("expectedVersion") Long expectedVersion,
                     @Param("now") Instant now);
}
