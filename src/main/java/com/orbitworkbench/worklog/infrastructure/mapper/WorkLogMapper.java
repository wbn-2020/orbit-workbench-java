package com.orbitworkbench.worklog.infrastructure.mapper;

import com.orbitworkbench.worklog.domain.WorkLogRecord;
import com.orbitworkbench.worklog.domain.WorkLogRow;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface WorkLogMapper {

    void insert(WorkLogRecord record);

    WorkLogRow findOwned(@Param("userId") Long userId, @Param("id") Long id);

    List<WorkLogRow> listByUser(@Param("userId") Long userId,
                                @Param("limit") int limit,
                                @Param("offset") int offset);

    int markDistilled(@Param("id") Long id,
                      @Param("userId") Long userId,
                      @Param("updatedAt") Instant updatedAt);
}
