package com.orbitworkbench.skill.api;

import com.orbitworkbench.skill.api.SkillDtos.SkillCommandRequest;
import com.orbitworkbench.skill.api.SkillDtos.SkillRequest;
import com.orbitworkbench.skill.api.SkillDtos.SkillResponse;
import com.orbitworkbench.skill.api.SkillDtos.SkillVersionRequest;
import com.orbitworkbench.skill.application.SkillService;
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
@RequestMapping("/api/v1/skills")
public class SkillController {

    private final SkillService service;

    public SkillController(SkillService service) {
        this.service = service;
    }

    @GetMapping
    public List<SkillResponse> list(@RequestParam Long workspaceId) {
        return service.list(workspaceId);
    }

    @PostMapping
    public SkillResponse create(@Valid @RequestBody SkillRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    public SkillResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public SkillResponse update(@PathVariable Long id,
                                @Valid @RequestBody SkillRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/versions")
    public SkillResponse createVersion(@PathVariable Long id,
                                       @Valid @RequestBody SkillVersionRequest request) {
        return service.createVersion(id, request);
    }

    @PostMapping("/{id}/publish")
    public SkillResponse publish(
            @PathVariable Long id,
            @RequestBody(required = false) SkillCommandRequest request) {
        return service.publish(id, request);
    }

    @PostMapping("/{id}/disable")
    public SkillResponse disable(@PathVariable Long id) {
        return service.disable(id);
    }
}
