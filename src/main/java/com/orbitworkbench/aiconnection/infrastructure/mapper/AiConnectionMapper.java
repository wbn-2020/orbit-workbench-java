package com.orbitworkbench.aiconnection.infrastructure.mapper;

import com.orbitworkbench.aiconnection.domain.AiConnectionRecord;
import com.orbitworkbench.aiconnection.domain.ConnectionTestRecord;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface AiConnectionMapper {
    Long findProviderId(@Param("providerType") String providerType);
    void insertConnection(AiConnectionRecord connection);
    void insertModelProfile(AiConnectionRecord connection);
    AiConnectionRecord findById(@Param("id") Long id);
    AiConnectionRecord findByIdIncludingDeleted(@Param("id") Long id);
    List<AiConnectionRecord> findPage(@Param("enabled") Boolean enabled,
                                      @Param("offset") int offset,
                                      @Param("limit") int limit);
    long countPage(@Param("enabled") Boolean enabled);
    int updateConnection(AiConnectionRecord connection);
    int updateModelProfile(AiConnectionRecord connection);
    int updateEnabled(@Param("id") Long id,
                      @Param("enabled") boolean enabled,
                      @Param("expectedVersion") long expectedVersion);
    int softDelete(@Param("id") Long id,
                   @Param("expectedVersion") long expectedVersion);
    int updateTestSummary(@Param("id") Long id,
                          @Param("expectedVersion") long expectedVersion,
                          @Param("status") String status,
                          @Param("testedAt") java.time.Instant testedAt,
                          @Param("latencyMs") Integer latencyMs,
                          @Param("errorCode") String errorCode,
                          @Param("errorSummary") String errorSummary);
    void insertTestRecord(ConnectionTestRecord record);
}
