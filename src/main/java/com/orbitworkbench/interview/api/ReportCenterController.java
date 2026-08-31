package com.orbitworkbench.interview.api;

import com.orbitworkbench.identity.application.OrbitUserDetails;
import com.orbitworkbench.interview.api.ReportCenterDtos.ReportDetailView;
import com.orbitworkbench.interview.api.ReportCenterDtos.ReportListResponse;
import com.orbitworkbench.interview.api.ReportCenterDtos.ReportSummaryResponse;
import com.orbitworkbench.interview.application.ReportCenterService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 报告中心读接口（14 §5）。只有查询：报告的生成与重试图仍在
 * {@code /interview-sessions/{id}/report} 上，避免同一个事实出现两个写入口。
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportCenterController {

    private final ReportCenterService service;

    public ReportCenterController(ReportCenterService service) {
        this.service = service;
    }

    @GetMapping
    public ReportListResponse list(@RequestParam(required = false) String days,
                                   @RequestParam(required = false) String topicMode,
                                   @RequestParam(required = false) String form,
                                   @RequestParam(required = false) String recommendation,
                                   @RequestParam(required = false) Long interviewerId,
                                   @RequestParam(required = false) String page,
                                   @RequestParam(required = false) String size,
                                   Authentication authentication) {
        return service.list(userId(authentication), days, topicMode, form, recommendation, interviewerId,
                page, size);
    }

    @GetMapping("/summary")
    public ReportSummaryResponse summary(@RequestParam(required = false) String days,
                                         @RequestParam(required = false) String ruleVersion,
                                         Authentication authentication) {
        return service.summary(userId(authentication), days, ruleVersion);
    }

    @GetMapping("/{id}")
    public ReportDetailView detail(@PathVariable Long id, Authentication authentication) {
        return service.detail(userId(authentication), id);
    }

    private Long userId(Authentication authentication) {
        return ((OrbitUserDetails) authentication.getPrincipal()).userId();
    }
}
