package com.orbitworkbench.agent.application;

import com.orbitworkbench.agent.domain.AgentRunStepRecord;
import com.orbitworkbench.agent.infrastructure.mapper.AgentRunStepMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentRunStepService {

    private final AgentRunStepMapper mapper;
    private final RunEventService eventService;
    private final SseHub sseHub;

    public AgentRunStepService(AgentRunStepMapper mapper,
                               RunEventService eventService,
                               SseHub sseHub) {
        this.mapper = mapper;
        this.eventService = eventService;
        this.sseHub = sseHub;
    }

    @Transactional
    public AgentRunStepRecord start(Long runId,
                                    String stepType,
                                    String title,
                                    String inputSummary) {
        Instant now = Instant.now();
        AgentRunStepRecord step = new AgentRunStepRecord();
        step.setAgentRunId(runId);
        step.setStepNumber(mapper.nextStepNumber(runId));
        step.setStepType(stepType);
        step.setTitle(trim(title, 255));
        step.setStatus("RUNNING");
        step.setInputSummary(trim(inputSummary, 512));
        step.setStartedAt(now);
        step.setCreatedAt(now);
        step.setUpdatedAt(now);
        mapper.insert(step);
        var event = eventService.append(
                runId,
                null,
                "run.step.started",
                step.getTitle(),
                Map.of(
                        "stepId", step.getId(),
                        "stepNumber", step.getStepNumber(),
                        "stepType", step.getStepType()));
        sseHub.publishAfterCommit(event);
        return step;
    }

    @Transactional
    public void attachModelCall(Long stepId, Long modelCallId) {
        if (mapper.attachModelCall(stepId, modelCallId, Instant.now()) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "Agent 步骤状态已变化");
        }
    }

    @Transactional
    public void attachToolCall(Long stepId, Long toolCallId) {
        if (mapper.attachToolCall(stepId, toolCallId, Instant.now()) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "Agent 步骤状态已变化");
        }
    }

    @Transactional
    public void complete(Long stepId, String outputSummary) {
        AgentRunStepRecord step = require(stepId);
        Instant now = Instant.now();
        if (mapper.complete(stepId, trim(outputSummary, 512), now) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "Agent 步骤状态已变化");
        }
        var event = eventService.append(
                step.getAgentRunId(),
                step.getModelCallId(),
                "run.step.completed",
                step.getTitle(),
                Map.of(
                        "stepId", stepId,
                        "stepNumber", step.getStepNumber(),
                        "stepType", step.getStepType()));
        sseHub.publishAfterCommit(event);
    }

    @Transactional
    public void fail(Long stepId, ErrorCode errorCode, String summary) {
        AgentRunStepRecord step = require(stepId);
        Instant now = Instant.now();
        String safeSummary = trim(summary, 512);
        if (mapper.fail(stepId, errorCode.name(), safeSummary, now) != 1) {
            return;
        }
        var event = eventService.append(
                step.getAgentRunId(),
                step.getModelCallId(),
                "run.step.failed",
                safeSummary,
                Map.of(
                        "stepId", stepId,
                        "stepNumber", step.getStepNumber(),
                        "errorCode", errorCode.name()));
        sseHub.publishAfterCommit(event);
    }

    @Transactional(readOnly = true)
    public List<AgentRunStepRecord> findByRunId(Long runId) {
        return mapper.findByRunId(runId);
    }

    @Transactional
    public void cancelRun(Long runId) {
        mapper.cancelRunningByRunId(runId, Instant.now());
    }

    private AgentRunStepRecord require(Long id) {
        AgentRunStepRecord step = mapper.findById(id);
        if (step == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "Agent 步骤不存在");
        }
        return step;
    }

    private String trim(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .trim();
        return normalized.length() <= maxLength
                ? normalized : normalized.substring(0, maxLength);
    }
}
