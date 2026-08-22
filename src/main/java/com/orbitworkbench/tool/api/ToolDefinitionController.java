package com.orbitworkbench.tool.api;

import com.orbitworkbench.tool.api.ToolDtos.ToolDefinitionResponse;
import com.orbitworkbench.tool.application.ToolQueryService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tool-definitions")
public class ToolDefinitionController {

    private final ToolQueryService service;

    public ToolDefinitionController(ToolQueryService service) {
        this.service = service;
    }

    @GetMapping
    public List<ToolDefinitionResponse> list() {
        return service.definitions();
    }

    @GetMapping("/{id}")
    public ToolDefinitionResponse get(@PathVariable Long id) {
        return service.definition(id);
    }
}
