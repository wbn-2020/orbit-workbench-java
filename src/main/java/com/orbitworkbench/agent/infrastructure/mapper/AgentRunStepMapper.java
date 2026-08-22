package com.orbitworkbench.agent.infrastructure.mapper;

import com.orbitworkbench.agent.domain.AgentRunStepRecord;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface AgentRunStepMapper {

    int nextStepNumber(@Param("runId") Long runId);

    void insert(AgentRunStepRecord step);

    List<AgentRunStepRecord> findByRunId(@Param("runId") Long runId);

    AgentRunStepRecord findById(@Param("id") Long id);

    int attachModelCall(@Param("id") Long id,
                        @Param("modelCallId") Long modelCallId,
                        @Param("updatedAt") Instant updatedAt);

    int attachToolCall(@Param("id") Long id,
                       @Param("toolCallId") Long toolCallId,
                       @Param("updatedAt") Instant updatedAt);

    int complete(@Param("id") Long id,
                 @Param("outputSummary") String outputSummary,
                 @Param("finishedAt") Instant finishedAt);

    int fail(@Param("id") Long id,
             @Param("errorCode") String errorCode,
             @Param("errorSummary") String errorSummary,
             @Param("finishedAt") Instant finishedAt);

    int cancelRunningByRunId(@Param("runId") Long runId,
                             @Param("finishedAt") Instant finishedAt);
}
