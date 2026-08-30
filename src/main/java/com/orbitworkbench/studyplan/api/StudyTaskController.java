package com.orbitworkbench.studyplan.api;

import com.orbitworkbench.identity.application.OrbitUserDetails;
import com.orbitworkbench.studyplan.application.StudyTaskService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/study-tasks")
public class StudyTaskController {

    private final StudyTaskService service;

    public StudyTaskController(StudyTaskService service) {
        this.service = service;
    }

    @PostMapping
    public StudyTaskDtos.TaskResponse create(@Valid @RequestBody StudyTaskDtos.CreateTaskRequest request,
                                             Authentication authentication) {
        return service.create(userId(authentication), request);
    }

    @GetMapping
    public List<StudyTaskDtos.TaskResponse> list(@RequestParam(required = false) String status,
                                                 Authentication authentication) {
        return service.list(userId(authentication), status);
    }

    @PostMapping("/{id}/start")
    public StudyTaskDtos.TaskResponse start(@PathVariable Long id, Authentication authentication) {
        return service.start(userId(authentication), id);
    }

    @PostMapping("/{id}/complete")
    public StudyTaskDtos.TaskResponse complete(@PathVariable Long id, Authentication authentication) {
        return service.complete(userId(authentication), id);
    }

    @PostMapping("/{id}/postpone")
    public StudyTaskDtos.TaskResponse postpone(@PathVariable Long id,
                                               @Valid @RequestBody(required = false) StudyTaskDtos.PostponeRequest request,
                                               Authentication authentication) {
        return service.postpone(userId(authentication), id, request == null ? null : request.dueDate());
    }

    @PostMapping("/{id}/skip")
    public StudyTaskDtos.TaskResponse skip(@PathVariable Long id, Authentication authentication) {
        return service.skip(userId(authentication), id);
    }

    @PostMapping("/from-report/{sessionId}")
    public Map<String, Object> generateFromReport(@PathVariable Long sessionId, Authentication authentication) {
        int created = service.generateFromReport(userId(authentication), sessionId);
        return Map.of("created", created);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id, Authentication authentication) {
        service.delete(userId(authentication), id);
    }

    private Long userId(Authentication authentication) {
        return ((OrbitUserDetails) authentication.getPrincipal()).userId();
    }
}
