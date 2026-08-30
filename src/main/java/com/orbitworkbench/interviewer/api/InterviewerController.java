package com.orbitworkbench.interviewer.api;

import com.orbitworkbench.identity.application.OrbitUserDetails;
import com.orbitworkbench.interviewer.api.InterviewerDtos.CopyInterviewerRequest;
import com.orbitworkbench.interviewer.api.InterviewerDtos.CreateInterviewerRequest;
import com.orbitworkbench.interviewer.api.InterviewerDtos.InterviewerResponse;
import com.orbitworkbench.interviewer.api.InterviewerDtos.UpdateInterviewerRequest;
import com.orbitworkbench.interviewer.application.InterviewerService;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/api/v1/interviewers")
public class InterviewerController {

    private final InterviewerService interviewerService;

    public InterviewerController(InterviewerService interviewerService) {
        this.interviewerService = interviewerService;
    }

    @GetMapping
    public List<InterviewerResponse> list(@RequestParam(required = false, defaultValue = "false")
                                          boolean includeArchived,
                                          Authentication authentication) {
        return interviewerService.list(userId(authentication), includeArchived);
    }

    @GetMapping("/{id}")
    public InterviewerResponse get(@PathVariable Long id, Authentication authentication) {
        return interviewerService.get(userId(authentication), id);
    }

    @PostMapping
    public InterviewerResponse create(@Valid @RequestBody CreateInterviewerRequest request,
                                      Authentication authentication) {
        return interviewerService.create(userId(authentication), request);
    }

    @PostMapping("/{id}/copy")
    public InterviewerResponse copy(@PathVariable Long id,
                                    @RequestBody(required = false) CopyInterviewerRequest request,
                                    Authentication authentication) {
        return interviewerService.copy(userId(authentication), id, request);
    }

    @PutMapping("/{id}")
    public InterviewerResponse update(@PathVariable Long id,
                                      @Valid @RequestBody UpdateInterviewerRequest request,
                                      Authentication authentication) {
        return interviewerService.update(userId(authentication), id, request);
    }

    @PostMapping("/{id}/archive")
    public InterviewerResponse archive(@PathVariable Long id, Authentication authentication) {
        return interviewerService.setArchived(userId(authentication), id, true);
    }

    @PostMapping("/{id}/unarchive")
    public InterviewerResponse unarchive(@PathVariable Long id, Authentication authentication) {
        return interviewerService.setArchived(userId(authentication), id, false);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id, Authentication authentication) {
        interviewerService.delete(userId(authentication), id);
    }

    private Long userId(Authentication authentication) {
        return ((OrbitUserDetails) authentication.getPrincipal()).userId();
    }
}
