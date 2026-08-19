package com.orbitworkbench.workspace.api;

import com.orbitworkbench.workspace.api.WorkspaceDtos.WorkspaceRequest;
import com.orbitworkbench.workspace.api.WorkspaceDtos.WorkspaceResponse;
import com.orbitworkbench.workspace.application.WorkspaceService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces")
public class WorkspaceController {
    private final WorkspaceService service;

    public WorkspaceController(WorkspaceService service) {
        this.service = service;
    }

    @GetMapping
    public List<WorkspaceResponse> list() { return service.list(); }

    @PostMapping
    public WorkspaceResponse create(@Valid @RequestBody WorkspaceRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    public WorkspaceResponse get(@PathVariable Long id) { return service.get(id); }

    @PutMapping("/{id}")
    public WorkspaceResponse update(@PathVariable Long id,
                                    @Valid @RequestBody WorkspaceRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) { service.delete(id); }
}

