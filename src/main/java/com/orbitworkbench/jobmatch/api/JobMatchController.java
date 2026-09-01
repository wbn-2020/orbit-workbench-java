package com.orbitworkbench.jobmatch.api;

import com.orbitworkbench.identity.application.OrbitUserDetails;
import com.orbitworkbench.jobmatch.api.JobMatchDtos.ConfirmationRequest;
import com.orbitworkbench.jobmatch.api.JobMatchDtos.MatchViewResponse;
import com.orbitworkbench.jobmatch.application.JobMatchService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 单次匹配结果的读取与人工确认（`17` §8）。确认是对既有行的状态更新，不新增行，
 * 也不重算结论——历史那一条必须永远是当时那份快照。
 */
@RestController
@RequestMapping("/api/v1/matches")
public class JobMatchController {

    private final JobMatchService service;

    public JobMatchController(JobMatchService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    public MatchViewResponse detail(@PathVariable Long id, Authentication authentication) {
        return service.matchDetail(userId(authentication), id);
    }

    @PostMapping("/{id}/confirmation")
    public MatchViewResponse confirm(@PathVariable Long id,
                                     @Valid @RequestBody ConfirmationRequest request,
                                     Authentication authentication) {
        return service.confirm(userId(authentication), id, request.status(), request.expectedStatus());
    }

    private Long userId(Authentication authentication) {
        return ((OrbitUserDetails) authentication.getPrincipal()).userId();
    }
}
