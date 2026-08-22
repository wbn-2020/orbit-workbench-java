package com.orbitworkbench.tool.api;

import com.orbitworkbench.tool.api.ToolDtos.ToolCallResponse;
import com.orbitworkbench.tool.application.ToolQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tool-calls")
public class ToolCallController {

    private final ToolQueryService service;

    public ToolCallController(ToolQueryService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    public ToolCallResponse get(@PathVariable Long id) {
        return service.call(id);
    }
}
