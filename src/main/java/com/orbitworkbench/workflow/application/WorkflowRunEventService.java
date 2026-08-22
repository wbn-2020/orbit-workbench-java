package com.orbitworkbench.workflow.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.workflow.domain.WorkflowRunEventRecord;
import com.orbitworkbench.workflow.infrastructure.mapper.WorkflowRunEventMapper;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowRunEventService {

    private static final int DEFAULT_LIMIT = 500;

    private final WorkflowRunEventMapper mapper;
    private final ObjectMapper objectMapper;

    public WorkflowRunEventService(WorkflowRunEventMapper mapper,
                                   ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public WorkflowRunEventRecord append(Long runId,
                                         String type,
                                         String summary,
                                         Object data) {
        if (mapper.incrementSequence(runId) != 1) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.WORKFLOW_RUN_NOT_FOUND,
                    "workflow 运行不存在");
        }
        Long sequence = mapper.currentSequence(runId);
        if (sequence == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.WORKFLOW_RUN_NOT_FOUND,
                    "workflow 运行不存在");
        }
        WorkflowRunEventRecord event = new WorkflowRunEventRecord();
        event.setWorkflowRunId(runId);
        event.setSequence(sequence);
        event.setEventType(type);
        event.setEventSummary(summary);
        event.setPayloadJson(writeJson(data));
        event.setOccurredAt(Instant.now());
        mapper.insert(event);
        return event;
    }

    @Transactional(readOnly = true)
    public List<WorkflowRunEventRecord> findAfter(Long runId,
                                                  long afterSequence,
                                                  int limit) {
        return mapper.findAfterSequence(
                runId,
                Math.max(afterSequence, 0L),
                Math.min(Math.max(limit, 1), 1000));
    }

    @Transactional(readOnly = true)
    public List<WorkflowRunEventRecord> findAfter(Long runId,
                                                  long afterSequence) {
        return findAfter(runId, afterSequence, DEFAULT_LIMIT);
    }

    private String writeJson(Object data) {
        try {
            return objectMapper.writeValueAsString(data == null ? java.util.Map.of() : data);
        } catch (JsonProcessingException exception) {
            return "{\"error\":\"workflow_event_payload_serialization_failed\"}";
        }
    }
}
