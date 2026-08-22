package com.orbitworkbench.artifactexport.api;

import com.orbitworkbench.artifactexport.api.ArtifactExportDtos.ArtifactExportResponse;
import com.orbitworkbench.artifactexport.api.ArtifactExportDtos.CreateArtifactExportRequest;
import com.orbitworkbench.artifactexport.application.ArtifactExportDownload;
import com.orbitworkbench.artifactexport.application.ArtifactExportService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ArtifactExportController {

    private final ArtifactExportService service;

    public ArtifactExportController(ArtifactExportService service) {
        this.service = service;
    }

    @PostMapping("/api/v1/artifacts/{artifactId}/exports")
    @ResponseStatus(HttpStatus.CREATED)
    public ArtifactExportResponse create(
            @PathVariable Long artifactId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateArtifactExportRequest request) {
        return service.create(artifactId, request, idempotencyKey);
    }

    @GetMapping("/api/v1/artifacts/{artifactId}/exports")
    public List<ArtifactExportResponse> list(@PathVariable Long artifactId) {
        return service.list(artifactId);
    }

    @GetMapping("/api/v1/artifact-exports/{exportId}")
    public ArtifactExportResponse get(@PathVariable Long exportId) {
        return service.get(exportId);
    }

    @GetMapping("/api/v1/artifact-exports/{exportId}/content")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long exportId) {
        ArtifactExportDownload download = service.download(exportId);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(download.mediaType())
                .contentLength(download.sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(download.inputStream()));
    }
}
