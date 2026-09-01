package com.orbitworkbench.jobmatch.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/**
 * 岗位与 JD 匹配契约（`17` §8）。枚举型入参一律用「String + {@code @Pattern}」再在服务层解析，
 * 不用枚举类型：Jackson 解不出枚举值会抛 HttpMessageNotReadableException 落到兜底 500，
 * 而「类别写错了」必须是 400 且说清可选值（沿用错题本 AttemptRequest 的做法）。
 */
public final class JobMatchDtos {

    private JobMatchDtos() {
    }

    public record RequirementPayload(
            @NotBlank @Size(max = 64) String id,
            @NotBlank @Pattern(regexp = "SKILL|EXPERIENCE|PROJECT|OTHER") String category,
            @NotBlank @Size(max = 128) String text
    ) {}

    public record CreatePostingRequest(
            @NotBlank @Size(max = 128) String company,
            @NotBlank @Size(max = 128) String title,
            @Size(max = 64) String city,
            @Size(max = 128) String salaryNote,
            @Size(max = 64) String source,
            Long applicationId,
            @NotBlank @Size(max = 20000) String jdText,
            @Size(max = 30) List<@Valid RequirementPayload> requirements
    ) {}

    public record MetaRequest(
            @NotBlank @Size(max = 128) String company,
            @NotBlank @Size(max = 128) String title,
            @Size(max = 64) String city,
            @Size(max = 128) String salaryNote,
            @Size(max = 64) String source,
            Long applicationId,
            @NotBlank String expectedUpdatedAt
    ) {}

    /** 改 JD 正文或改要求清单都出新版本（两者同属一个版本，`17` §6）。 */
    public record JdVersionRequest(
            @NotBlank @Size(max = 20000) String jdText,
            @Size(max = 30) List<@Valid RequirementPayload> requirements
    ) {}

    public record ActiveVersionRequest(
            @NotNull Long versionId
    ) {}

    public record SaveMatchRequest(
            Long resumeVersionId
    ) {}

    public record ConfirmationRequest(
            @NotBlank @Pattern(regexp = "PENDING|CONFIRMED|REJECTED") String status,
            @Pattern(regexp = "PENDING|CONFIRMED|REJECTED") String expectedStatus
    ) {}

    public record ApplicationRef(
            Long id,
            String company,
            String role,
            String stage
    ) {}

    public record RequirementResponse(
            String id,
            String category,
            String categoryLabel,
            String text
    ) {}

    public record PostingSummaryResponse(
            Long id,
            String company,
            String title,
            String city,
            String salaryNote,
            String source,
            boolean archived,
            Instant updatedAt,
            Long activeVersionId,
            Integer activeVersionNumber,
            int requirementCount,
            ApplicationRef application,
            Instant lastMatchedAt,
            String lastMatchConfirmationStatus
    ) {}

    public record PostingListResponse(
            List<PostingSummaryResponse> items,
            long total,
            int page,
            int size,
            int maxRequirementCount,
            int maxJdChars
    ) {}

    public record VersionSummaryResponse(
            Long id,
            int versionNumber,
            String ruleVersion,
            int requirementCount,
            int jdChars,
            boolean active,
            Instant createdAt
    ) {}

    public record PostingDetailResponse(
            Long id,
            String company,
            String title,
            String city,
            String salaryNote,
            String source,
            boolean archived,
            Instant createdAt,
            Instant updatedAt,
            ApplicationRef application,
            Long activeVersionId,
            String jdText,
            List<RequirementResponse> requirements,
            String ruleVersion,
            List<VersionSummaryResponse> versions,
            ScoringResponse scoring,
            String scoringNote
    ) {}

    public record EvidenceResponse(
            String sectionKey,
            String sectionLabel,
            String itemId,
            String itemLabel,
            String snippet
    ) {}

    public record RequirementResultResponse(
            String requirementId,
            String category,
            String categoryLabel,
            String text,
            String verdict,
            String verdictLabel,
            String reason,
            List<EvidenceResponse> evidences,
            int evidenceCount
    ) {}

    public record ResumeRef(
            Long versionId,
            Integer versionNumber,
            String status
    ) {}

    /** 匹配分数按 ADR-0011 本期不产出：{@code enabled=false} 时四个分数键都不会出现在响应里。 */
    public record ScoringResponse(
            boolean enabled,
            String reason
    ) {}

    public record CountsResponse(
            int total,
            int matched,
            int gaps,
            int needConfirmation
    ) {}

    public record MatchViewResponse(
            Long matchId,
            Long jobId,
            String company,
            String title,
            Long jdVersionId,
            int jdVersionNumber,
            String ruleVersion,
            ResumeRef resume,
            String resumeSource,
            Instant profileSnapshotAt,
            CountsResponse counts,
            ScoringResponse scoring,
            String confirmationStatus,
            Instant confirmedAt,
            Instant savedAt,
            List<RequirementResultResponse> items
    ) {}

    public record MatchHistoryResponse(
            Long matchId,
            int jdVersionNumber,
            ResumeRef resume,
            String ruleVersion,
            CountsResponse counts,
            String confirmationStatus,
            Instant confirmedAt,
            Instant createdAt
    ) {}

    public record MatchListResponse(
            List<MatchHistoryResponse> items,
            long total,
            int page,
            int size
    ) {}
}
