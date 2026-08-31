package com.orbitworkbench.resume.api;

import com.orbitworkbench.identity.application.OrbitUserDetails;
import com.orbitworkbench.resume.api.ResumeDtos.ActiveRequest;
import com.orbitworkbench.resume.api.ResumeDtos.BootstrapRequest;
import com.orbitworkbench.resume.api.ResumeDtos.DraftSaveRequest;
import com.orbitworkbench.resume.api.ResumeDtos.FinalizeRequest;
import com.orbitworkbench.resume.api.ResumeDtos.PreflightResponse;
import com.orbitworkbench.resume.api.ResumeDtos.ResumeStateResponse;
import com.orbitworkbench.resume.api.ResumeDtos.VersionDetail;
import com.orbitworkbench.resume.application.ResumeExportService;
import com.orbitworkbench.resume.application.ResumeService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/resume")
public class ResumeController {

    private final ResumeService service;
    private final ResumeExportService exportService;

    public ResumeController(ResumeService service, ResumeExportService exportService) {
        this.service = service;
        this.exportService = exportService;
    }

    @GetMapping
    public ResumeStateResponse state(Authentication authentication) {
        return service.state(userId(authentication));
    }

    @PostMapping("/bootstrap")
    public VersionDetail bootstrap(
            @RequestBody(required = false) BootstrapRequest request, Authentication authentication) {
        return service.bootstrap(userId(authentication),
                request == null ? null : request.title());
    }

    @GetMapping("/versions/{id}")
    public VersionDetail detail(@PathVariable Long id, Authentication authentication) {
        return service.detail(userId(authentication), id);
    }

    @PutMapping("/draft")
    public VersionDetail saveDraft(@Valid @RequestBody DraftSaveRequest request,
                                   Authentication authentication) {
        return service.saveDraft(userId(authentication), request);
    }

    @PostMapping("/draft/finalize")
    public VersionDetail finalizeDraft(
            @RequestBody(required = false) FinalizeRequest request, Authentication authentication) {
        return service.finalizeDraft(userId(authentication),
                request == null ? null : request.changeSummary());
    }

    @PostMapping("/versions/{id}/duplicate")
    public VersionDetail duplicate(@PathVariable Long id, Authentication authentication) {
        return service.duplicate(userId(authentication), id);
    }

    @PutMapping("/active")
    public ResumeStateResponse setActive(@Valid @RequestBody ActiveRequest request,
                                         Authentication authentication) {
        return service.setActive(userId(authentication), request);
    }

    @GetMapping("/versions/{id}/export-preflight")
    public PreflightResponse preflight(@PathVariable Long id, Authentication authentication) {
        return exportService.preflight(userId(authentication), id);
    }

    @PostMapping("/versions/{id}/pdf")
    public VersionDetail export(@PathVariable Long id, Authentication authentication) {
        Long userId = userId(authentication);
        exportService.export(userId, id);
        return service.detail(userId, id);
    }

    @GetMapping("/versions/{id}/pdf")
    public ResponseEntity<byte[]> download(@PathVariable Long id, Authentication authentication) {
        ResumeExportService.Downloadable download = exportService.download(userId(authentication), id);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(download.content());
    }

    private Long userId(Authentication authentication) {
        return ((OrbitUserDetails) authentication.getPrincipal()).userId();
    }
}
