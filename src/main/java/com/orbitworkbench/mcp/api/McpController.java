package com.orbitworkbench.mcp.api;

import com.orbitworkbench.mcp.api.McpDtos.McpServerRequest;
import com.orbitworkbench.mcp.api.McpDtos.McpServerResponse;
import com.orbitworkbench.mcp.api.McpDtos.McpToolResponse;
import com.orbitworkbench.mcp.application.McpServerService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/mcp")
public class McpController {

    private final McpServerService service;

    public McpController(McpServerService service) {
        this.service = service;
    }

    @GetMapping("/servers")
    public List<McpServerResponse> list(
            @RequestParam Long workspaceId) {
        return service.list(workspaceId);
    }

    @PostMapping("/servers")
    public McpServerResponse create(
            @Valid @RequestBody McpServerRequest request) {
        return service.create(request);
    }

    @GetMapping("/servers/{id}")
    public McpServerResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PutMapping("/servers/{id}")
    public McpServerResponse update(@PathVariable Long id,
                                    @Valid @RequestBody McpServerRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/servers/{id}/sync")
    public McpServerResponse sync(@PathVariable Long id) {
        return service.sync(id);
    }

    @PostMapping("/servers/{id}/enable")
    public McpServerResponse enable(@PathVariable Long id) {
        return service.enable(id);
    }

    @PostMapping("/servers/{id}/disable")
    public McpServerResponse disable(@PathVariable Long id) {
        return service.disable(id);
    }

    @GetMapping("/servers/{id}/tools")
    public List<McpToolResponse> tools(@PathVariable Long id) {
        return service.tools(id);
    }

    @PostMapping("/tools/{id}/enable")
    public McpToolResponse enableTool(@PathVariable Long id) {
        return service.enableTool(id);
    }

    @PostMapping("/tools/{id}/disable")
    public McpToolResponse disableTool(@PathVariable Long id) {
        return service.disableTool(id);
    }
}
