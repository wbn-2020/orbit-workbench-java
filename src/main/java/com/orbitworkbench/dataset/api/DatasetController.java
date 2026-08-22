package com.orbitworkbench.dataset.api;

import com.orbitworkbench.dataset.api.DatasetDtos.DatasetColumnResponse;
import com.orbitworkbench.dataset.api.DatasetDtos.DatasetDetailResponse;
import com.orbitworkbench.dataset.api.DatasetDtos.DatasetPreviewResponse;
import com.orbitworkbench.dataset.api.DatasetDtos.DatasetProfileResponse;
import com.orbitworkbench.dataset.api.DatasetDtos.DatasetSheetResponse;
import com.orbitworkbench.dataset.api.DatasetDtos.DatasetSummaryResponse;
import com.orbitworkbench.dataset.api.DatasetDtos.UpdateDatasetColumnRequest;
import com.orbitworkbench.dataset.application.DatasetService;
import com.orbitworkbench.shared.api.PageResult;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/datasets")
public class DatasetController {

    private final DatasetService datasetService;

    public DatasetController(DatasetService datasetService) {
        this.datasetService = datasetService;
    }

    @GetMapping
    public PageResult<DatasetSummaryResponse> list(
            @RequestParam(required = false) Long workspaceId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String format,
            @RequestParam(required = false) String updatedFrom,
            @RequestParam(required = false) String updatedTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return datasetService.findPage(
                workspaceId, status, format, updatedFrom, updatedTo, page, size);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DatasetSummaryResponse upload(
            @RequestParam Long workspaceId,
            @RequestParam(required = false) String name,
            @RequestPart("file") MultipartFile file) {
        return datasetService.upload(workspaceId, name, file);
    }

    @GetMapping("/{id}")
    public DatasetDetailResponse get(@PathVariable Long id) {
        return datasetService.get(id);
    }

    @PostMapping("/{id}/parse")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DatasetSummaryResponse parse(@PathVariable Long id) {
        return datasetService.queueParse(id, false);
    }

    @PostMapping("/{id}/reparse")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DatasetSummaryResponse reparse(@PathVariable Long id) {
        return datasetService.queueParse(id, true);
    }

    @GetMapping("/{id}/sheets")
    public List<DatasetSheetResponse> sheets(@PathVariable Long id) {
        return datasetService.sheets(id);
    }

    @GetMapping("/{id}/sheets/{sheetId}")
    public DatasetSheetResponse sheet(@PathVariable Long id,
                                      @PathVariable Long sheetId) {
        return datasetService.sheet(id, sheetId);
    }

    @GetMapping("/{id}/sheets/{sheetId}/columns")
    public List<DatasetColumnResponse> columns(@PathVariable Long id,
                                               @PathVariable Long sheetId) {
        return datasetService.columns(id, sheetId);
    }

    @GetMapping("/{id}/sheets/{sheetId}/preview")
    public DatasetPreviewResponse preview(
            @PathVariable Long id,
            @PathVariable Long sheetId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "50") int limit) {
        return datasetService.preview(id, sheetId, offset, limit);
    }

    @GetMapping("/{id}/sheets/{sheetId}/profile")
    public DatasetProfileResponse profile(@PathVariable Long id,
                                          @PathVariable Long sheetId) {
        return datasetService.profile(id, sheetId);
    }

    @PutMapping("/{id}/sheets/{sheetId}/columns/{columnId}")
    public DatasetColumnResponse updateColumn(
            @PathVariable Long id,
            @PathVariable Long sheetId,
            @PathVariable Long columnId,
            @Valid @RequestBody UpdateDatasetColumnRequest request) {
        return datasetService.updateColumn(id, sheetId, columnId, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        datasetService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
