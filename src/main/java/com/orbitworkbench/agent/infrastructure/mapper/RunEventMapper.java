package com.orbitworkbench.agent.infrastructure.mapper;

import com.orbitworkbench.agent.domain.RunEventRecord;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface RunEventMapper {

    void insert(RunEventRecord event);

    int incrementSequence(@Param("runId") Long runId);

    Long currentSequence(@Param("runId") Long runId);

    List<RunEventRecord> findAfterSequence(@Param("runId") Long runId,
                                           @Param("afterSequence") long afterSequence,
                                           @Param("limit") int limit);
}
