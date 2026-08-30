package com.orbitworkbench.project.api;

import com.orbitworkbench.identity.application.OrbitUserDetails;
import com.orbitworkbench.project.api.ProjectDtos.ProjectDetailResponse;
import com.orbitworkbench.project.api.ProjectDtos.GitHubProjectImportRequest;
import com.orbitworkbench.project.api.ProjectDtos.GitHubVersionImportRequest;
import com.orbitworkbench.project.api.ProjectDtos.ProjectSummaryResponse;
import com.orbitworkbench.project.application.ProjectService;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService service;

    public ProjectController(ProjectService service) {
        this.service = service;
    }

    @GetMapping
    public List<ProjectSummaryResponse> list(Authentication authentication) {
        return service.list(userId(authentication));
    }

    @GetMapping("/{projectId}")
    public ProjectDetailResponse get(@PathVariable Long projectId,
                                     Authentication authentication) {
        return service.get(userId(authentication), projectId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProjectDetailResponse create(@RequestParam Long workspaceId,
                                        @RequestParam String name,
                                        @RequestPart("file") MultipartFile file,
                                        Authentication authentication) {
        return service.create(userId(authentication), workspaceId, name, file);
    }

    @PostMapping(value = "/imports/github", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ProjectDetailResponse createFromGitHub(
            @Valid @RequestBody GitHubProjectImportRequest request,
            Authentication authentication) {
        return service.createFromGitHub(userId(authentication), request.workspaceId(),
                request.name(), request.repositoryUrl());
    }

    @PostMapping(value = "/{projectId}/versions",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProjectDetailResponse importVersion(@PathVariable Long projectId,
                                               @RequestPart("file") MultipartFile file,
                                               Authentication authentication) {
        return service.importVersion(userId(authentication), projectId, file);
    }

    @PostMapping(value = "/{projectId}/versions/github",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ProjectDetailResponse importGitHubVersion(@PathVariable Long projectId,
                                                     @Valid @RequestBody GitHubVersionImportRequest request,
                                                     Authentication authentication) {
        return service.importGitHubVersion(userId(authentication), projectId, request.repositoryUrl());
    }

    private Long userId(Authentication authentication) {
        return ((OrbitUserDetails) authentication.getPrincipal()).userId();
    }
}
