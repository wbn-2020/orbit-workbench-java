package com.orbitworkbench.knowledge.api;

import com.orbitworkbench.identity.application.OrbitUserDetails;
import com.orbitworkbench.knowledge.application.KnowledgeOverviewService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 知识总览（V2-B1）：三类知识资产的只读聚合，帮助用户在一张页面上理解自己积累了什么。 */
@RestController
@RequestMapping("/api/v1/knowledge-overview")
public class KnowledgeOverviewController {

    private final KnowledgeOverviewService service;

    public KnowledgeOverviewController(KnowledgeOverviewService service) {
        this.service = service;
    }

    @GetMapping
    public KnowledgeOverviewService.KnowledgeOverviewResponse overview(Authentication authentication) {
        return service.overview(userId(authentication));
    }

    private Long userId(Authentication authentication) {
        return ((OrbitUserDetails) authentication.getPrincipal()).userId();
    }
}
