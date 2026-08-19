package com.orbitworkbench.aiconnection.infrastructure.mapper;

import com.orbitworkbench.aiconnection.domain.ConnectionTestRecord;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ConnectionTestRecordMapper {

    void insert(ConnectionTestRecord record);

    List<ConnectionTestRecord> findPage(@Param("connectionId") Long connectionId,
                                        @Param("offset") int offset,
                                        @Param("limit") int limit);

    long countPage(@Param("connectionId") Long connectionId);
}
