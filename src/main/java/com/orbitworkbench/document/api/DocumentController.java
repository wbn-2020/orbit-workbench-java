package com.orbitworkbench.document.api;

import com.orbitworkbench.document.api.DocumentDtos.DocumentContent;
import com.orbitworkbench.document.api.DocumentDtos.DocumentDetailResponse;
import com.orbitworkbench.document.api.DocumentDtos.DocumentSummaryResponse;
import com.orbitworkbench.document.api.DocumentDtos.StorageCleanupFailureResponse;
import com.orbitworkbench.document.application.DocumentService;
import com.orbitworkbench.shared.api.PageResult;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping
    public PageResult<DocumentSummaryResponse> list(
            @RequestParam(required = false) Long workspaceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return documentService.findPage(workspaceId, page, size);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentSummaryResponse upload(@RequestParam Long workspaceId,
                                          @RequestPart("file") MultipartFile file) {
        return documentService.upload(workspaceId, file);
    }

    @GetMapping("/{id}")
    public DocumentDetailResponse get(@PathVariable Long id) {
        return documentService.get(id);
    }

    @GetMapping(value = "/{id}/content", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> content(@PathVariable Long id) {
        DocumentContent documentContent = documentService.readContent(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(documentContent.mediaType()))
                .body(documentContent.content());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        documentService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/storage-cleanup-failures")
    public PageResult<StorageCleanupFailureResponse> cleanupFailures(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return documentService.findCleanupFailures(status, page, size);
    }

    @PostMapping("/storage-cleanup-failures/{failureId}/retry")
    public StorageCleanupFailureResponse retryCleanupFailure(@PathVariable Long failureId) {
        return documentService.retryCleanupFailure(failureId);
    }
}
