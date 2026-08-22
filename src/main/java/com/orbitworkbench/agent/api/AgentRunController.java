package com.orbitworkbench.agent.api;

import com.orbitworkbench.agent.api.AgentRunDtos.AgentRunResponse;
import com.orbitworkbench.agent.api.AgentRunDtos.AgentRunStepResponse;
import com.orbitworkbench.agent.api.AgentRunDtos.RunCommandResponse;
import com.orbitworkbench.agent.api.AgentRunDtos.RunCreatedResponse;
import com.orbitworkbench.agent.application.AgentRunService;
import com.orbitworkbench.agent.application.AgentRunStepService;
import com.orbitworkbench.agent.application.SseHub;
import com.orbitworkbench.shared.api.PageResult;
import com.orbitworkbench.task.application.TaskService;
import com.orbitworkbench.tool.api.ToolDtos.ToolCallResponse;
import com.orbitworkbench.tool.application.ToolQueryService;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/agent-runs")
public class AgentRunController {

    private final AgentRunService agentRunService;
    private final SseHub sseHub;
    private final TaskService taskService;
    private final AgentRunStepService stepService;
    private final ToolQueryService toolQueryService;

    public AgentRunController(AgentRunService agentRunService,
                              SseHub sseHub,
                              TaskService taskService,
                              AgentRunStepService stepService,
                              ToolQueryService toolQueryService) {
        this.agentRunService = agentRunService;
        this.sseHub = sseHub;
        this.taskService = taskService;
        this.stepService = stepService;
        this.toolQueryService = toolQueryService;
    }

    @GetMapping("/{id}")
    public AgentRunResponse get(@PathVariable Long id) {
        return agentRunService.get(id);
    }

    @PostMapping("/{id}/pause")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RunCommandResponse pause(@PathVariable Long id) {
        AgentRunResponse run = agentRunService.pause(id);
        return RunCommandResponse.from(run, taskService.requireTask(run.taskId()).getStatus(), "PAUSE");
    }

    @PostMapping("/{id}/resume")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RunCommandResponse resume(@PathVariable Long id) {
        AgentRunResponse run = agentRunService.resume(id);
        return RunCommandResponse.from(run, taskService.requireTask(run.taskId()).getStatus(), "RESUME");
    }

    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RunCommandResponse cancel(@PathVariable Long id) {
        AgentRunResponse run = agentRunService.cancel(id);
        return RunCommandResponse.from(run, taskService.requireTask(run.taskId()).getStatus(), "CANCEL");
    }

    @PostMapping("/{id}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RunCommandResponse retry(@PathVariable Long id) {
        AgentRunResponse run = agentRunService.retry(id);
        return RunCommandResponse.from(run, taskService.requireTask(run.taskId()).getStatus(), "RETRY");
    }

    @GetMapping(value = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable Long id,
                             @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
                             @RequestParam(required = false) Long afterSequence) {
        agentRunService.get(id);
        long sequence = afterSequence == null
                ? parseSequence(lastEventId)
                : Math.max(afterSequence, 0L);
        return sseHub.subscribe(id, sequence);
    }

    @GetMapping("/{id}/steps")
    public List<AgentRunStepResponse> steps(@PathVariable Long id) {
        agentRunService.get(id);
        return stepService.findByRunId(id).stream()
                .map(step -> new AgentRunStepResponse(
                        step.getId(),
                        step.getAgentRunId(),
                        step.getStepNumber(),
                        step.getStepType(),
                        step.getTitle(),
                        step.getStatus(),
                        step.getModelCallId(),
                        step.getToolCallId(),
                        step.getInputSummary(),
                        step.getOutputSummary(),
                        step.getStartedAt(),
                        step.getFinishedAt(),
                        step.getErrorCode(),
                        step.getErrorSummary()))
                .toList();
    }

    @GetMapping("/{id}/tool-calls")
    public PageResult<ToolCallResponse> toolCalls(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        agentRunService.get(id);
        return toolQueryService.calls(id, page, size);
    }

    private long parseSequence(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Math.max(Long.parseLong(value), 0L);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }
}
