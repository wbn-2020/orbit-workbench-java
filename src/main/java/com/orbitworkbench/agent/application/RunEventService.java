package com.orbitworkbench.agent.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.agent.domain.RunEventRecord;
import com.orbitworkbench.agent.infrastructure.mapper.RunEventMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RunEventService {

    private static final int DEFAULT_REPLAY_LIMIT = 500;

    private final RunEventMapper runEventMapper;
    private final ObjectMapper objectMapper;

    public RunEventService(RunEventMapper runEventMapper, ObjectMapper objectMapper) {
        this.runEventMapper = runEventMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RunEventRecord append(Long runId,
                                 Long modelCallId,
                                 String type,
                                 String summary,
                                 Object data) {
        if (runEventMapper.incrementSequence(runId) != 1) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "运行不存在");
        }
        Long nextSequence = runEventMapper.currentSequence(runId);
        if (nextSequence == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "运行不存在");
        }
        RunEventRecord event = new RunEventRecord();
        event.setAgentRunId(runId);
        event.setModelCallId(modelCallId);
        event.setSequence(nextSequence);
        event.setEventType(type);
        event.setEventSummary(summary);
        event.setPayloadJson(writeJson(data));
        event.setOccurredAt(Instant.now());
        runEventMapper.insert(event);
        return event;
    }

    @Transactional(readOnly = true)
    public List<RunEventRecord> findAfter(Long runId, long afterSequence) {
        return findAfter(runId, afterSequence, DEFAULT_REPLAY_LIMIT);
    }

    @Transactional(readOnly = true)
    public List<RunEventRecord> findAfter(Long runId, long afterSequence, int limit) {
        int normalizedLimit = Math.min(Math.max(limit, 1), 1000);
        return runEventMapper.findAfterSequence(
                runId, Math.max(afterSequence, 0), normalizedLimit);
    }

    @Transactional(readOnly = true)
    public long latestSequence(Long runId) {
        Long sequence = runEventMapper.currentSequence(runId);
        return sequence == null ? 0L : sequence;
    }

    private String writeJson(Object data) {
        if (data == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException exception) {
            return "{\"error\":\"event_payload_serialization_failed\"}";
        }
    }
}
