package com.orbitworkbench.workflow.api;

import com.orbitworkbench.workflow.api.WorkflowDtos.WorkflowNodeRunResponse;
import com.orbitworkbench.workflow.api.WorkflowDtos.WorkflowRunEventResponse;
import com.orbitworkbench.workflow.api.WorkflowDtos.WorkflowRunResponse;
import com.orbitworkbench.workflow.application.WorkflowRunService;
import java.util.List;
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

    public WorkflowRunController(WorkflowRunService runService) {
        this.runService = runService;
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

    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public WorkflowRunResponse cancel(@PathVariable Long id) {
        return runService.cancel(id);
    }
}
