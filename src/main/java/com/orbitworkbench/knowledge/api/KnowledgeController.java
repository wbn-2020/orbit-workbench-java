package com.orbitworkbench.knowledge.api;

import com.orbitworkbench.identity.application.OrbitUserDetails;
import com.orbitworkbench.knowledge.application.KnowledgeBuildService;
import com.orbitworkbench.knowledge.application.KnowledgeService;
import com.orbitworkbench.knowledge.application.ProjectFactService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;
    private final KnowledgeBuildService knowledgeBuildService;
    private final ProjectFactService factService;

    public KnowledgeController(KnowledgeService knowledgeService,
                               KnowledgeBuildService knowledgeBuildService,
                               ProjectFactService factService) {
        this.knowledgeService = knowledgeService;
        this.knowledgeBuildService = knowledgeBuildService;
        this.factService = factService;
    }

    @PostMapping("/projects/{projectId}/versions/{versionId}/knowledge/build")
    public KnowledgeDtos.BuildResultResponse build(
            @PathVariable Long projectId,
            @PathVariable Long versionId,
            Authentication authentication) {
        return knowledgeBuildService.rebuild(userId(authentication), projectId, versionId);
    }

    @PostMapping("/knowledge/ask")
    public KnowledgeDtos.AskResponse ask(
            @Valid @RequestBody KnowledgeDtos.AskRequest request,
            Authentication authentication) {
        return knowledgeService.ask(userId(authentication), request.question(), request.projectVersionId());
    }

    @PostMapping("/projects/{projectId}/versions/{versionId}/facts/generate")
    public KnowledgeDtos.FactsListResponse generateFacts(
            @PathVariable Long projectId,
            @PathVariable Long versionId,
            @Valid @RequestBody(required = false) KnowledgeDtos.GenerateFactsRequest request,
            Authentication authentication) {
        return factService.generate(userId(authentication), projectId, versionId,
                request == null ? null : request.connectionId());
    }

    @GetMapping("/projects/{projectId}/versions/{versionId}/facts")
    public KnowledgeDtos.FactsListResponse facts(
            @PathVariable Long projectId,
            @PathVariable Long versionId,
            Authentication authentication) {
        return factService.list(userId(authentication), versionId);
    }

    @PostMapping("/projects/{projectId}/versions/{versionId}/facts/{factId}/confirm")
    public KnowledgeDtos.FactResponse confirmFact(
            @PathVariable Long projectId,
            @PathVariable Long versionId,
            @PathVariable Long factId,
            @Valid @RequestBody KnowledgeDtos.ConfirmFactRequest request,
            Authentication authentication) {
        return factService.confirm(userId(authentication), projectId, versionId, factId, request);
    }

    @PostMapping("/projects/{projectId}/versions/{versionId}/facts/{factId}/archive")
    public KnowledgeDtos.FactResponse archiveFact(
            @PathVariable Long projectId,
            @PathVariable Long versionId,
            @PathVariable Long factId,
            Authentication authentication) {
        return factService.archive(userId(authentication), projectId, versionId, factId);
    }

    private Long userId(Authentication authentication) {
        return ((OrbitUserDetails) authentication.getPrincipal()).userId();
    }
}
