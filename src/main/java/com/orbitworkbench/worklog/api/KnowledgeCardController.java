package com.orbitworkbench.worklog.api;

import com.orbitworkbench.identity.application.OrbitUserDetails;
import com.orbitworkbench.worklog.api.KnowledgeCardDtos.KnowledgeCardResponse;
import com.orbitworkbench.worklog.api.KnowledgeCardDtos.UpdateKnowledgeCardRequest;
import com.orbitworkbench.worklog.application.KnowledgeCardService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 知识卡片接口：列表与人工编辑（标题/摘要/标签），蒸馏写入路径独立。 */
@RestController
@RequestMapping("/api/v1/knowledge-cards")
public class KnowledgeCardController {

    private final KnowledgeCardService service;
    private final com.orbitworkbench.preference.application.PreferenceService preferenceService;

    public KnowledgeCardController(KnowledgeCardService service,
                                   com.orbitworkbench.preference.application.PreferenceService preferenceService) {
        this.service = service;
        this.preferenceService = preferenceService;
    }

    /** 当日到期卡片（按用户时区判定今天；date 参数用于跨时区客户端显式指定）。 */
    @GetMapping("/due")
    public com.orbitworkbench.worklog.api.KnowledgeCardDtos.DueListResponse due(
            @org.springframework.web.bind.annotation.RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate date,
            Authentication authentication) {
        java.time.LocalDate today = date != null ? date
                : java.time.LocalDate.now(preferenceService.timezone(userId(authentication)));
        return service.dueCards(userId(authentication), today);
    }

    /** 记一次回顾：阶梯推进 1/3/7/14 天。 */
    @PostMapping("/{id}/review")
    public com.orbitworkbench.worklog.api.KnowledgeCardDtos.ReviewResponse review(
            @PathVariable Long id,
            Authentication authentication) {
        java.time.LocalDate today = java.time.LocalDate.now(preferenceService.timezone(userId(authentication)));
        return service.review(userId(authentication), id, today);
    }

    @GetMapping
    public List<KnowledgeCardResponse> list(Authentication authentication) {
        return service.list(userId(authentication));
    }

    @PutMapping("/{id}")
    public KnowledgeCardResponse update(@PathVariable Long id,
                                        @Valid @RequestBody UpdateKnowledgeCardRequest request,
                                        Authentication authentication) {
        return service.update(userId(authentication), id, request);
    }

    private Long userId(Authentication authentication) {
        return ((OrbitUserDetails) authentication.getPrincipal()).userId();
    }
}
