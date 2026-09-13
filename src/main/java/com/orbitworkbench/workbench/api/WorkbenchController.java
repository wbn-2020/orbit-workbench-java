package com.orbitworkbench.workbench.api;

import com.orbitworkbench.identity.application.OrbitUserDetails;
import com.orbitworkbench.workbench.api.ReflectionDtos.ReflectionResponse;
import com.orbitworkbench.workbench.api.WorkbenchDtos.WorkbenchSummaryResponse;
import com.orbitworkbench.workbench.application.ReflectionService;
import com.orbitworkbench.workbench.application.WorkbenchService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 工作台首页聚合与周期复盘接口（v2）。 */
@RestController
@RequestMapping("/api/v1/workbench")
public class WorkbenchController {

    private final WorkbenchService service;
    private final ReflectionService reflectionService;

    public WorkbenchController(WorkbenchService service, ReflectionService reflectionService) {
        this.service = service;
        this.reflectionService = reflectionService;
    }

    @GetMapping("/summary")
    public WorkbenchSummaryResponse summary(Authentication authentication) {
        return service.summary(userId(authentication));
    }

    /**
     * 周期复盘：period=week|month，offset=0 为当前周期、-1 上一周期，最多回溯 24 个周期。
     * 只读聚合，与首页共用认证边界。
     */
    @GetMapping("/reflection")
    public ReflectionResponse reflection(@RequestParam(defaultValue = "week") String period,
                                         @RequestParam(defaultValue = "0") int offset,
                                         Authentication authentication) {
        return reflectionService.reflect(userId(authentication), period, offset);
    }

    private Long userId(Authentication authentication) {
        return ((OrbitUserDetails) authentication.getPrincipal()).userId();
    }
}
