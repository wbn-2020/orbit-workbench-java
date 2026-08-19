package com.orbitworkbench.artifact.api;

import com.orbitworkbench.artifact.api.ArtifactDtos.ArtifactDetailResponse;
import com.orbitworkbench.artifact.api.ArtifactDtos.ArtifactSummaryResponse;
import com.orbitworkbench.artifact.api.ArtifactDtos.ArtifactVersionResponse;
import com.orbitworkbench.artifact.api.ArtifactDtos.ArtifactVersionSummaryResponse;
import com.orbitworkbench.artifact.api.ArtifactDtos.UpdateArtifactRequest;
import com.orbitworkbench.artifact.application.ArtifactService;
import com.orbitworkbench.shared.api.PageResult;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/artifacts")
public class ArtifactController {

    private final ArtifactService artifactService;

    public ArtifactController(ArtifactService artifactService) {
        this.artifactService = artifactService;
    }

    @GetMapping
    public PageResult<ArtifactSummaryResponse> list(
            @RequestParam(required = false) Long workspaceId,
            @RequestParam(required = false) Long taskId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return artifactService.findPage(workspaceId, taskId, page, size);
    }

    @GetMapping("/{id}")
    public ArtifactDetailResponse get(@PathVariable Long id) {
        return artifactService.get(id);
    }

    @GetMapping("/{id}/versions")
    public List<ArtifactVersionSummaryResponse> versions(@PathVariable Long id) {
        return artifactService.versions(id);
    }

    @GetMapping("/{id}/versions/{versionId}")
    public ArtifactVersionResponse version(@PathVariable Long id,
                                           @PathVariable Long versionId) {
        return artifactService.version(id, versionId);
    }

    @PutMapping("/{id}")
    public ArtifactDetailResponse update(@PathVariable Long id,
                                         @Valid @RequestBody UpdateArtifactRequest request) {
        return artifactService.update(id, request);
    }
}
