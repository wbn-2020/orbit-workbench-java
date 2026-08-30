package com.orbitworkbench.jobapplication.api;

import com.orbitworkbench.identity.application.OrbitUserDetails;
import com.orbitworkbench.jobapplication.application.JobApplicationService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/job-applications")
public class JobApplicationController {

    private final JobApplicationService service;

    public JobApplicationController(JobApplicationService service) {
        this.service = service;
    }

    @PostMapping
    public JobApplicationDtos.ApplicationResponse create(
            @Valid @RequestBody JobApplicationDtos.CreateRequest request,
            Authentication authentication) {
        return service.create(userId(authentication), request);
    }

    @GetMapping
    public JobApplicationDtos.ApplicationListResponse list(
            @RequestParam(defaultValue = "false") boolean includeArchived,
            Authentication authentication) {
        return service.list(userId(authentication), includeArchived);
    }

    @GetMapping("/{id}")
    public JobApplicationDtos.ApplicationResponse get(@PathVariable Long id, Authentication authentication) {
        return service.get(userId(authentication), id);
    }

    @PutMapping("/{id}")
    public JobApplicationDtos.ApplicationResponse update(
            @PathVariable Long id,
            @Valid @RequestBody JobApplicationDtos.UpdateRequest request,
            Authentication authentication) {
        return service.update(userId(authentication), id, request);
    }

    @PostMapping("/{id}/stage")
    public JobApplicationDtos.ApplicationResponse advance(
            @PathVariable Long id,
            @Valid @RequestBody JobApplicationDtos.StageRequest request,
            Authentication authentication) {
        return service.advance(userId(authentication), id, request);
    }

    @PostMapping("/{id}/archive")
    public JobApplicationDtos.ApplicationResponse archive(@PathVariable Long id, Authentication authentication) {
        return service.setArchived(userId(authentication), id, true);
    }

    @PostMapping("/{id}/unarchive")
    public JobApplicationDtos.ApplicationResponse unarchive(@PathVariable Long id, Authentication authentication) {
        return service.setArchived(userId(authentication), id, false);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id, Authentication authentication) {
        service.delete(userId(authentication), id);
    }

    private Long userId(Authentication authentication) {
        return ((OrbitUserDetails) authentication.getPrincipal()).userId();
    }
}
