package com.orbitworkbench.agent.api;

import com.orbitworkbench.agent.api.AgentDtos.AgentCommandRequest;
import com.orbitworkbench.agent.api.AgentDtos.AgentRequest;
import com.orbitworkbench.agent.api.AgentDtos.AgentResponse;
import com.orbitworkbench.agent.api.AgentDtos.AgentVersionRequest;
import com.orbitworkbench.agent.application.AgentRunService;
import com.orbitworkbench.agent.application.AgentService;
import com.orbitworkbench.agent.api.AgentRunDtos.AgentRunResponse;
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
@RequestMapping("/api/v1/agents")
public class AgentController {

    private final AgentService agentService;
    private final AgentRunService agentRunService;

    public AgentController(AgentService agentService, AgentRunService agentRunService) {
        this.agentService = agentService;
        this.agentRunService = agentRunService;
    }

    @GetMapping
    public List<AgentResponse> list(@RequestParam(required = false) Long workspaceId) {
        return agentService.list(workspaceId);
    }

    @PostMapping
    public AgentResponse create(@Valid @RequestBody AgentRequest request) {
        return agentService.create(request);
    }

    @GetMapping("/{id}")
    public AgentResponse get(@PathVariable Long id) {
        return agentService.get(id);
    }

    @PutMapping("/{id}")
    public AgentResponse update(@PathVariable Long id,
                                @Valid @RequestBody AgentRequest request) {
        return agentService.update(id, request);
    }

    @PostMapping("/{id}/versions")
    public AgentResponse createVersion(@PathVariable Long id,
                                       @Valid @RequestBody AgentVersionRequest request) {
        return agentService.createVersion(id, request);
    }

    @PostMapping("/{id}/publish")
    public AgentResponse publish(
            @PathVariable Long id,
            @RequestBody(required = false) AgentCommandRequest request) {
        return agentService.publish(id, request);
    }

    @PostMapping("/{id}/disable")
    public AgentResponse disable(
            @PathVariable Long id,
            @RequestBody(required = false) AgentCommandRequest request) {
        return agentService.disable(id, request);
    }

    @GetMapping("/{id}/runs")
    public List<AgentRunResponse> runs(@PathVariable Long id) {
        agentService.get(id);
        return agentRunService.listByAgentDefinition(id);
    }
}
