package com.orbitworkbench.aiconnection.api;

import com.orbitworkbench.aiconnection.application.AiConnectionService;
import com.orbitworkbench.aiconnection.application.AiUsageService;
import com.orbitworkbench.identity.application.OrbitUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** AI 用量与费用账本端点。 */
@RestController
@RequestMapping("/api/v1/ai-usage")
public class AiUsageController {

    private final AiUsageService usageService;
    private final AiConnectionService connectionService;

    public AiUsageController(AiUsageService usageService, AiConnectionService connectionService) {
        this.usageService = usageService;
        this.connectionService = connectionService;
    }

    @GetMapping
    public AiUsageService.UsageResponse usage(
            @RequestParam(required = false) Integer days,
            Authentication authentication) {
        Long userId = userId(authentication);
        // 有单价才谈得上成本；界面据此区分「未配置单价」与「确实为 0」
        boolean anyPricing = connectionService.hasAnyPricingConfigured();
        return usageService.usage(userId, days, anyPricing);
    }

    private Long userId(Authentication authentication) {
        return ((OrbitUserDetails) authentication.getPrincipal()).userId();
    }
}
