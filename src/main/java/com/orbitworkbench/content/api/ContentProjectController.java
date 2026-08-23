package com.orbitworkbench.content.api;

import com.orbitworkbench.content.api.ContentDtos.ContentMaterialRequest;
import com.orbitworkbench.content.api.ContentDtos.ContentOperationRequest;
import com.orbitworkbench.content.api.ContentDtos.ContentProjectRequest;
import com.orbitworkbench.content.api.ContentDtos.ContentProjectResponse;
import com.orbitworkbench.content.api.ContentDtos.ContentProjectSummaryResponse;
import com.orbitworkbench.content.api.ContentDtos.ContentVersionResponse;
import com.orbitworkbench.content.application.ContentProjectService;
import com.orbitworkbench.shared.api.PageResult;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/content-projects")
public class ContentProjectController {

    private final ContentProjectService service;

    public ContentProjectController(ContentProjectService service) {
        this.service = service;
    }

    @GetMapping
    public PageResult<ContentProjectSummaryResponse> list(
            @RequestParam(required = false) Long workspaceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(workspaceId, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContentProjectResponse create(
            @Valid @RequestBody ContentProjectRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    public ContentProjectResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public ContentProjectResponse update(
            @PathVariable Long id,
            @Valid @RequestBody ContentProjectRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/materials")
    public ContentProjectResponse addMaterial(
            @PathVariable Long id,
            @Valid @RequestBody ContentMaterialRequest request) {
        return service.addMaterial(id, request);
    }

    @DeleteMapping("/{id}/materials/{materialId}")
    public ContentProjectResponse removeMaterial(
            @PathVariable Long id,
            @PathVariable Long materialId) {
        return service.removeMaterial(id, materialId);
    }

    @PostMapping("/{id}/generate")
    public ContentVersionResponse generate(
            @PathVariable Long id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ContentOperationRequest request) {
        return service.generate(id, request, idempotencyKey);
    }

    @PostMapping("/{id}/review")
    public ContentVersionResponse review(
            @PathVariable Long id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ContentOperationRequest request) {
        return service.review(id, request, idempotencyKey);
    }

    @GetMapping("/{id}/versions")
    public List<ContentVersionResponse> versions(@PathVariable Long id) {
        return service.versions(id);
    }

    @GetMapping("/{id}/versions/{versionId}")
    public ContentVersionResponse version(@PathVariable Long id,
                                          @PathVariable Long versionId) {
        return service.version(id, versionId);
    }
}
