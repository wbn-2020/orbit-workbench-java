package com.orbitworkbench.workflow.api;

import com.orbitworkbench.workflow.api.WorkflowDtos.WorkflowCommandRequest;
import com.orbitworkbench.workflow.api.WorkflowDtos.WorkflowRequest;
import com.orbitworkbench.workflow.api.WorkflowDtos.WorkflowResponse;
import com.orbitworkbench.workflow.api.WorkflowDtos.WorkflowRunRequest;
import com.orbitworkbench.workflow.api.WorkflowDtos.WorkflowRunResponse;
import com.orbitworkbench.workflow.api.WorkflowDtos.WorkflowUpdateRequest;
import com.orbitworkbench.workflow.api.WorkflowDtos.WorkflowValidationResponse;
import com.orbitworkbench.workflow.api.WorkflowDtos.WorkflowVersionRequest;
import com.orbitworkbench.workflow.api.WorkflowDtos.WorkflowVersionResponse;
import com.orbitworkbench.workflow.application.WorkflowRunService;
import com.orbitworkbench.workflow.application.WorkflowService;
import com.orbitworkbench.shared.api.PageResult;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workflows")
public class WorkflowController {

    private final WorkflowService workflowService;
    private final WorkflowRunService runService;

    public WorkflowController(WorkflowService workflowService,
                              WorkflowRunService runService) {
        this.workflowService = workflowService;
        this.runService = runService;
    }

    @GetMapping
    public PageResult<WorkflowResponse> list(
            @RequestParam Long workspaceId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return workflowService.findPageByWorkspaceId(workspaceId, status, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowResponse create(@Valid @RequestBody WorkflowRequest request) {
        return workflowService.create(request);
    }

    @GetMapping("/{id}")
    public WorkflowResponse get(@PathVariable Long id) {
        return workflowService.get(id);
    }

    @PutMapping("/{id}")
    public WorkflowResponse update(@PathVariable Long id,
                                   @Valid @RequestBody WorkflowUpdateRequest request) {
        return workflowService.update(id, request);
    }

    @PostMapping("/{id}/validate")
    public WorkflowValidationResponse validate(
            @PathVariable Long id,
            @RequestBody(required = false) WorkflowVersionRequest request) {
        return workflowService.validateDraft(
                id,
                request == null ? null : request.graph());
    }

    @PostMapping("/{id}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowVersionResponse createVersion(
            @PathVariable Long id,
            @Valid @RequestBody WorkflowVersionRequest request) {
        return workflowService.createVersion(id, request);
    }

    @GetMapping("/{id}/versions/{versionId}")
    public WorkflowVersionResponse getVersion(@PathVariable Long id,
                                              @PathVariable Long versionId) {
        return workflowService.getVersion(id, versionId);
    }

    @PutMapping("/{id}/versions/{versionId}")
    public WorkflowVersionResponse updateVersion(
            @PathVariable Long id,
            @PathVariable Long versionId,
            @Valid @RequestBody WorkflowVersionRequest request) {
        return workflowService.updateVersion(id, versionId, request);
    }

    @GetMapping("/{id}/versions/{versionId}/validation")
    public WorkflowValidationResponse validateVersion(@PathVariable Long id,
                                                      @PathVariable Long versionId) {
        return workflowService.validateVersion(id, versionId);
    }

    @PostMapping("/{id}/publish")
    public WorkflowResponse publish(
            @PathVariable Long id,
            @RequestBody(required = false) WorkflowCommandRequest request) {
        return workflowService.publish(
                id, request == null ? new WorkflowCommandRequest(null, null) : request);
    }

    @PostMapping("/{id}/disable")
    public WorkflowResponse disable(@PathVariable Long id) {
        return workflowService.disable(id);
    }

    @PostMapping("/{id}/runs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public WorkflowRunResponse createRun(
            @PathVariable Long id,
            @RequestBody(required = false) WorkflowRunRequest request) {
        return runService.create(id, request == null ? new WorkflowRunRequest(null, null) : request);
    }

    @GetMapping("/{id}/runs")
    public PageResult<WorkflowRunResponse> runs(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return runService.findPage(id, page, size);
    }
}
