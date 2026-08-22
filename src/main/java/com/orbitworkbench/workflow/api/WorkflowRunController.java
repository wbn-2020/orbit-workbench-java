package com.orbitworkbench.workflow.api;

import com.orbitworkbench.workflow.api.WorkflowDtos.WorkflowNodeRunResponse;
import com.orbitworkbench.workflow.api.WorkflowDtos.WorkflowRunEventResponse;
import com.orbitworkbench.workflow.api.WorkflowDtos.WorkflowRunResponse;
import com.orbitworkbench.workflow.application.WorkflowRunService;
import com.orbitworkbench.tool.api.ToolDtos.ToolCallResponse;
import com.orbitworkbench.tool.application.ToolQueryService;
import java.util.List;
import com.orbitworkbench.shared.api.PageResult;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workflow-runs")
public class WorkflowRunController {

    private final WorkflowRunService runService;
    private final ToolQueryService toolQueryService;

    public WorkflowRunController(WorkflowRunService runService,
                                 ToolQueryService toolQueryService) {
        this.runService = runService;
        this.toolQueryService = toolQueryService;
    }

    @GetMapping("/{id}")
    public WorkflowRunResponse get(@PathVariable Long id) {
        return runService.get(id);
    }

    @GetMapping("/{id}/nodes")
    public List<WorkflowNodeRunResponse> nodes(@PathVariable Long id) {
        return runService.nodeRuns(id);
    }

    @GetMapping("/{id}/events")
    public List<WorkflowRunEventResponse> events(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") long afterSequence,
            @RequestParam(defaultValue = "500") int limit) {
        return runService.events(id, afterSequence, limit);
    }

    @GetMapping("/{id}/tool-calls")
    public PageResult<ToolCallResponse> toolCalls(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int size) {
        runService.get(id);
        return toolQueryService.workflowCalls(id, page, size);
    }

    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public WorkflowRunResponse cancel(@PathVariable Long id) {
        return runService.cancel(id);
    }
}
