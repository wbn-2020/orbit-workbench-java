package com.orbitworkbench.agent.api;

import com.orbitworkbench.agent.api.AgentCatalogDtos.AgentDefinitionResponse;
import com.orbitworkbench.agent.api.AgentCatalogDtos.PromptVersionResponse;
import com.orbitworkbench.agent.application.AgentCatalogService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AgentCatalogController {

    private final AgentCatalogService service;

    public AgentCatalogController(AgentCatalogService service) {
        this.service = service;
    }

    @GetMapping("/agent-definitions")
    public List<AgentDefinitionResponse> definitions() {
        return service.definitions();
    }

    @GetMapping("/agent-definitions/{id}")
    public AgentDefinitionResponse definition(@PathVariable Long id) {
        return service.definition(id);
    }

    @GetMapping("/prompt-templates/{templateId}/versions")
    public List<PromptVersionResponse> promptVersions(
            @PathVariable Long templateId) {
        return service.promptVersions(templateId);
    }
}
