package com.orbitworkbench.workbench.api;

import com.orbitworkbench.identity.application.OrbitUserDetails;
import com.orbitworkbench.workbench.api.ReflectionDtos.ReflectionResponse;
import com.orbitworkbench.workbench.api.WorkbenchDtos.WorkbenchSummaryResponse;
import com.orbitworkbench.workbench.application.GrowthThreadService;
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
    private final GrowthThreadService growthThreadService;

    public WorkbenchController(WorkbenchService service, ReflectionService reflectionService,
                               GrowthThreadService growthThreadService) {
        this.service = service;
        this.reflectionService = reflectionService;
        this.growthThreadService = growthThreadService;
    }

    @GetMapping("/summary")
    public WorkbenchSummaryResponse summary(Authentication authentication) {
        return service.summary(userId(authentication));
    }

    /**
     * V55：成长脉络——把 V49/V50/V52/V54 与各模块既有的溯源链接聚合成链（只读，零迁移）。
     * 链上只画数据库里真实存在的关系，断了的环节如实缺位。
     */
    @GetMapping("/growth-threads")
    public GrowthThreadDtos.GrowthThreadsResponse growthThreads(Authentication authentication) {
        return new GrowthThreadDtos.GrowthThreadsResponse(
                growthThreadService.threads(userId(authentication)));
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
