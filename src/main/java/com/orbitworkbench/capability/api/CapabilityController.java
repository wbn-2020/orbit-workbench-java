package com.orbitworkbench.capability.api;

import com.orbitworkbench.capability.api.CapabilityDtos.CapabilityOverviewResponse;
import com.orbitworkbench.capability.api.CapabilityDtos.DimensionEvidenceResponse;
import com.orbitworkbench.capability.application.CapabilityService;
import com.orbitworkbench.identity.application.OrbitUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 技能图谱读接口（`16` §8）。只有查询：本模块不写任何表，
 * 报告的产生与重试图仍只在 {@code /interview-sessions/{id}/report} 上。
 */
@RestController
@RequestMapping("/api/v1/capabilities")
public class CapabilityController {

    private final CapabilityService service;

    public CapabilityController(CapabilityService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public CapabilityOverviewResponse overview(@RequestParam(required = false) String days,
                                               @RequestParam(required = false) String ruleVersion,
                                               Authentication authentication) {
        return service.overview(userId(authentication), days, ruleVersion);
    }

    /** 路径变量是中文维度名，前端按 UTF-8 百分号编码传输；词表外的一律 404（`16` §8）。 */
    @GetMapping("/dimensions/{name}")
    public DimensionEvidenceResponse evidence(@PathVariable String name,
                                              @RequestParam(required = false) String days,
                                              @RequestParam(required = false) String ruleVersion,
                                              Authentication authentication) {
        return service.evidence(userId(authentication), name, days, ruleVersion);
    }

    private Long userId(Authentication authentication) {
        return ((OrbitUserDetails) authentication.getPrincipal()).userId();
    }
}
