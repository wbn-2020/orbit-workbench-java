package com.orbitworkbench.task.api;

import com.orbitworkbench.agent.api.AgentRunDtos.RunCreatedResponse;
import com.orbitworkbench.agent.api.AgentRunDtos.AgentRunResponse;
import com.orbitworkbench.agent.application.AgentRunService;
import com.orbitworkbench.shared.api.PageResult;
import com.orbitworkbench.task.api.TaskDtos.CreateTaskRequest;
import com.orbitworkbench.task.api.TaskDtos.TaskResponse;
import com.orbitworkbench.task.api.TaskDtos.UpdateTaskRequest;
import com.orbitworkbench.task.application.TaskService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskService taskService;
    private final AgentRunService agentRunService;

    public TaskController(TaskService taskService, AgentRunService agentRunService) {
        this.taskService = taskService;
        this.agentRunService = agentRunService;
    }

    @GetMapping
    public PageResult<TaskResponse> list(
            @RequestParam(required = false) Long workspaceId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String moduleType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return taskService.findPage(workspaceId, status, moduleType, page, size);
    }

    @PostMapping
    public TaskResponse create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateTaskRequest request) {
        return taskService.create(request, idempotencyKey);
    }

    @GetMapping("/{id}")
    public TaskResponse get(@PathVariable Long id) {
        return taskService.get(id);
    }

    @PutMapping("/{id}")
    public TaskResponse update(@PathVariable Long id, @Valid @RequestBody UpdateTaskRequest request) {
        return taskService.update(id, request);
    }

    @PostMapping("/{id}/runs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public RunCreatedResponse start(@PathVariable Long id) {
        return agentRunService.start(id, null);
    }

    @GetMapping("/{id}/runs")
    public List<AgentRunResponse> runs(@PathVariable Long id) {
        return agentRunService.listByTask(id);
    }
}
