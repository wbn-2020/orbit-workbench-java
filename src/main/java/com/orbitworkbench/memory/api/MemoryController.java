package com.orbitworkbench.memory.api;

import com.orbitworkbench.memory.api.MemoryDtos.MemoryCandidateRequest;
import com.orbitworkbench.memory.api.MemoryDtos.MemoryCandidateResponse;
import com.orbitworkbench.memory.api.MemoryDtos.MemoryCommandRequest;
import com.orbitworkbench.memory.api.MemoryDtos.MemoryRequest;
import com.orbitworkbench.memory.api.MemoryDtos.MemoryResponse;
import com.orbitworkbench.memory.application.MemoryService;
import com.orbitworkbench.shared.api.PageResult;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/memories")
public class MemoryController {

    private final MemoryService service;

    public MemoryController(MemoryService service) {
        this.service = service;
    }

    @GetMapping
    public PageResult<MemoryResponse> list(
            @RequestParam(required = false) Long workspaceId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String memoryType,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(workspaceId, status, memoryType, q, page, size);
    }

    @PostMapping
    public MemoryResponse create(@Valid @RequestBody MemoryRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    public MemoryResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public MemoryResponse update(@PathVariable Long id,
                                 @Valid @RequestBody MemoryRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/archive")
    public MemoryResponse archive(@PathVariable Long id,
                                  @RequestBody(required = false) MemoryCommandRequest request) {
        return service.archive(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       @RequestBody(required = false) MemoryCommandRequest request) {
        service.delete(id, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/candidates")
    public PageResult<MemoryCandidateResponse> candidates(
            @RequestParam(required = false) Long workspaceId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.candidates(workspaceId, status, page, size);
    }

    @PostMapping("/candidates")
    public MemoryCandidateResponse propose(
            @Valid @RequestBody MemoryCandidateRequest request) {
        return service.propose(request);
    }

    @PostMapping("/candidates/{id}/confirm")
    public MemoryResponse confirm(@PathVariable Long id,
                                  @RequestBody(required = false) MemoryCommandRequest request) {
        return service.confirmCandidate(id, request);
    }

    @PostMapping("/candidates/{id}/reject")
    public MemoryCandidateResponse reject(@PathVariable Long id,
                                          @RequestBody(required = false) MemoryCommandRequest request) {
        return service.rejectCandidate(id, request);
    }

    @PostMapping("/candidates/{id}/archive")
    public MemoryCandidateResponse archiveCandidate(
            @PathVariable Long id,
            @RequestBody(required = false) MemoryCommandRequest request) {
        return service.archiveCandidate(id, request);
    }

    @GetMapping("/injection")
    public List<MemoryResponse> injection(
            @RequestParam Long workspaceId,
            @RequestParam(defaultValue = "20") int limit) {
        return service.injection(workspaceId, limit);
    }
}
