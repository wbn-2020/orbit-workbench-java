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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 知识卡片接口：列表与人工编辑（标题/摘要/标签），蒸馏写入路径独立。 */
@RestController
@RequestMapping("/api/v1/knowledge-cards")
public class KnowledgeCardController {

    private final KnowledgeCardService service;

    public KnowledgeCardController(KnowledgeCardService service) {
        this.service = service;
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
