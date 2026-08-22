package com.orbitworkbench.analysis.api;

import com.orbitworkbench.analysis.api.DataAnalysisDtos.CreateDataAnalysisTaskRequest;
import com.orbitworkbench.analysis.api.DataAnalysisDtos.DataAnalysisTaskResponse;
import com.orbitworkbench.analysis.api.DataAnalysisDtos.UpdateDataAnalysisTaskRequest;
import com.orbitworkbench.analysis.application.DataAnalysisTaskService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/data-analysis/tasks")
public class DataAnalysisTaskController {

    private final DataAnalysisTaskService service;

    public DataAnalysisTaskController(DataAnalysisTaskService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DataAnalysisTaskResponse create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateDataAnalysisTaskRequest request) {
        return service.create(request, idempotencyKey);
    }

    @GetMapping("/{taskId}")
    public DataAnalysisTaskResponse get(@PathVariable Long taskId) {
        return service.get(taskId);
    }

    @PutMapping("/{taskId}")
    public DataAnalysisTaskResponse update(
            @PathVariable Long taskId,
            @Valid @RequestBody UpdateDataAnalysisTaskRequest request) {
        return service.update(taskId, request);
    }
}
