package com.orbitworkbench.jobprofile.api;

import com.orbitworkbench.identity.application.OrbitUserDetails;
import com.orbitworkbench.jobprofile.api.JobProfileDtos.JobProfileRequest;
import com.orbitworkbench.jobprofile.api.JobProfileDtos.JobProfileResponse;
import com.orbitworkbench.jobprofile.api.JobProfileDtos.JobProfileStateResponse;
import com.orbitworkbench.jobprofile.application.JobProfileService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/job-profile")
public class JobProfileController {

    private final JobProfileService service;

    public JobProfileController(JobProfileService service) {
        this.service = service;
    }

    @GetMapping
    public JobProfileStateResponse get(Authentication authentication) {
        return service.get(userId(authentication));
    }

    @PutMapping
    public JobProfileResponse save(@Valid @RequestBody JobProfileRequest request,
                                   Authentication authentication) {
        return service.save(userId(authentication), request);
    }

    private Long userId(Authentication authentication) {
        return ((OrbitUserDetails) authentication.getPrincipal()).userId();
    }
}
