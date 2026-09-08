package com.orbitworkbench.focus.api;

import com.orbitworkbench.identity.application.OrbitUserDetails;
import com.orbitworkbench.focus.api.FocusDtos.FocusStatResponse;
import com.orbitworkbench.focus.application.FocusSessionService;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 专注趋势统计接口（v2 专注计时域），按天聚合近 N 日专注时长与次数。 */
@RestController
@RequestMapping("/api/v1/focus-stats")
public class FocusStatsController {

    private final FocusSessionService service;

    public FocusStatsController(FocusSessionService service) {
        this.service = service;
    }

    @GetMapping
    public List<FocusStatResponse> stats(@RequestParam(required = false) String days,
                                         Authentication authentication) {
        return service.stats(userId(authentication), days);
    }

    private Long userId(Authentication authentication) {
        return ((OrbitUserDetails) authentication.getPrincipal()).userId();
    }
}
