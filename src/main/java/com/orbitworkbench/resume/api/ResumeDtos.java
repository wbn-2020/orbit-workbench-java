package com.orbitworkbench.resume.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class ResumeDtos {

    private ResumeDtos() {
    }

    public record SourcePayload(
            @NotBlank @Size(max = 32) String type,
            Long refId,
            @Size(max = 128) String label,
            Long projectId,
            Long projectVersionId
    ) {}

    public record ItemPayload(
            @NotBlank @Size(max = 64) String id,
            @NotNull Integer order,
            @NotBlank @Size(max = 16) String kind,
            @Size(max = 128) String label,
            @NotBlank @Size(max = 2000) String text,
            @Valid SourcePayload source,
            Boolean edited
    ) {}

    public record SectionPayload(
            @NotBlank @Size(max = 32) String key,
            @Size(max = 30) List<@Valid ItemPayload> items
    ) {}

    public record DraftSaveRequest(
            @NotNull @Size(max = 5) List<@Valid SectionPayload> sections,
            Instant expectedUpdatedAt,
            @Size(max = 128) String title,
            @Size(max = 255) String changeSummary
    ) {}

    public record FinalizeRequest(
            @Size(max = 255) String changeSummary
    ) {}

    public record ActiveRequest(
            @NotNull Long versionId
    ) {}

    public record BootstrapRequest(
            @Size(max = 128) String title
    ) {}

    public record ItemResponse(
            String id,
            int order,
            String kind,
            String label,
            String text,
            String sourceType,
            Long sourceRefId,
            String sourceLabel,
            Long sourceProjectId,
            Long sourceProjectVersionId,
            boolean edited
    ) {}

    public record SectionResponse(
            String key,
            List<ItemResponse> items
    ) {}

    public record VersionSummary(
            Long id,
            int versionNumber,
            String status,
            String changeSummary,
            int sectionCount,
            int itemCount,
            int totalChars,
            boolean pdfAvailable,
            Instant pdfGeneratedAt,
            long exportCount,
            Instant lastExportedAt,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record VersionDetail(
            Long id,
            int versionNumber,
            String status,
            String changeSummary,
            List<SectionResponse> sections,
            List<ExportRecordResponse> exports,
            Instant pdfGeneratedAt,
            Instant sourceSnapshotAt,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record ExportRecordResponse(
            Long id,
            String status,
            Long sizeBytes,
            String fontName,
            String failureReason,
            Instant createdAt
    ) {}

    public record ResumeStateResponse(
            boolean exists,
            Long resumeId,
            String title,
            Long activeVersionId,
            VersionSummary activeVersion,
            VersionDetail draft,
            List<VersionSummary> versions
    ) {}

    public record PreflightResponse(
            boolean ready,
            List<String> blockers,
            int totalChars,
            int itemCount
    ) {}
}
