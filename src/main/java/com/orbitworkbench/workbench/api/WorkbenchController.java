package com.orbitworkbench.workbench.api;

import com.orbitworkbench.identity.application.OrbitUserDetails;
import com.orbitworkbench.workbench.api.WorkbenchDtos.WorkbenchSummaryResponse;
import com.orbitworkbench.workbench.application.WorkbenchService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 工作台首页聚合接口（v2）。 */
@RestController
@RequestMapping("/api/v1/workbench")
public class WorkbenchController {

    private final WorkbenchService service;

    public WorkbenchController(WorkbenchService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public WorkbenchSummaryResponse summary(Authentication authentication) {
        return service.summary(userId(authentication));
    }

    private Long userId(Authentication authentication) {
        return ((OrbitUserDetails) authentication.getPrincipal()).userId();
    }
}
