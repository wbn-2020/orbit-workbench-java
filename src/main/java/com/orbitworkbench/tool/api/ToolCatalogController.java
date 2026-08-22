package com.orbitworkbench.tool.api;

import com.orbitworkbench.tool.api.ToolCatalogDtos.ToolCatalogResponse;
import com.orbitworkbench.tool.api.ToolCatalogDtos.ToolCommandRequest;
import com.orbitworkbench.tool.api.ToolCatalogDtos.ToolVersionRequest;
import com.orbitworkbench.tool.application.ToolCatalogService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tools")
public class ToolCatalogController {

    private final ToolCatalogService service;

    public ToolCatalogController(ToolCatalogService service) {
        this.service = service;
    }

    @GetMapping
    public List<ToolCatalogResponse> list(
            @RequestParam(required = false) String status) {
        return service.list(status);
    }

    @GetMapping("/{id}")
    public ToolCatalogResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/{id}/versions")
    public ToolCatalogResponse createVersion(@PathVariable Long id,
                                             @Valid @RequestBody ToolVersionRequest request) {
        return service.createVersion(id, request);
    }

    @PostMapping("/{id}/publish")
    public ToolCatalogResponse publish(
            @PathVariable Long id,
            @RequestBody(required = false) ToolCommandRequest request) {
        return service.publish(id, request);
    }

    @PostMapping("/{id}/enable")
    public ToolCatalogResponse enable(@PathVariable Long id) {
        return service.enable(id);
    }

    @PostMapping("/{id}/disable")
    public ToolCatalogResponse disable(@PathVariable Long id) {
        return service.disable(id);
    }
}
