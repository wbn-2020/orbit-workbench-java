package com.orbitworkbench.interview.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.orbitworkbench.interview.domain.InterviewReportRecord;
import com.orbitworkbench.interview.domain.InterviewSessionRecord;
import com.orbitworkbench.interview.domain.InterviewTurnRecord;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class InterviewDtos {
    private InterviewDtos() {}

    public record ProjectBindingRequest(
            @NotNull Long projectId,
            @NotNull Long versionId
    ) {}

    public record CreateSessionRequest(
            @NotBlank @Size(max = 128) String title,
            @NotBlank @Pattern(regexp = "ROTE|PROJECT_DEEP_DIVE|AI_TECH|CODE_REVIEW|FULL_PROCESS|TRANSITION_TEACHING")
            String topicMode,
            @NotBlank @Pattern(regexp = "TRAINING|FORMAL") String form,
            @NotBlank @Pattern(regexp = "FIRST|SECOND|THIRD|CUSTOM") String round,
            Long interviewerId,
            @Size(max = 64) String interviewerName,
            @Size(max = 128) String targetRole,
            @Pattern(regexp = "GRADUATE|ONE_TO_THREE_YEARS|THREE_TO_FIVE_YEARS|FIVE_PLUS_YEARS|CUSTOM")
            String targetExperienceBand,
            @NotNull @Min(1) @Max(50) Integer questionLimit,
            @NotNull @Min(0) @Max(20) Integer followUpLimit,
            @NotNull @Min(1) @Max(100) Integer turnLimit,
            @NotNull @Min(5) @Max(240) Integer durationLimitMinutes,
            Instant scheduledAt,
            Long aiConnectionId,
            @Pattern(regexp = "DISABLED|ON_DEMAND|AUTO") String webSearchPolicy,
            @Size(max = 5) List<@Valid ProjectBindingRequest> projectBindings,
            @Size(max = 10) List<@NotNull Long> knowledgeCardIds
    ) {}

    public record NextQuestionRequest(
            @NotNull @Pattern(regexp = "MAIN|FOLLOW_UP") String turnType,
            @Size(max = 200) String instruction
    ) {}

    public record AddTurnRequest(
            @NotNull @Pattern(regexp = "MAIN|FOLLOW_UP") String turnType,
            @NotBlank @Size(max = 4000) String question
    ) {}

    public record SubmitAnswerRequest(
            @NotBlank @Size(max = 20000) String answer,
            @NotBlank @Pattern(regexp = "INDEPENDENT|PROMPTED|AI_ASSISTED|AI_GENERATED|HISTORY_IMPORT")
            String answerSource
    ) {}

    public record ProjectBindingSnapshotResponse(
            Long projectId,
            String projectName,
            Long versionId,
            Integer versionNumber,
            int factCount
    ) {}

    public record WebSearchOutcomeResponse(
            String requested,
            String dialect,
            String applied,
            String note
    ) {}

    public record SessionResponse(
            Long id,
            String title,
            String topicMode,
            String form,
            String round,
            Long interviewerId,
            String interviewerName,
            Long aiConnectionId,
            String aiModel,
            String webSearchPolicy,
            WebSearchOutcomeResponse webSearchOutcome,
            String targetRole,
            String targetExperienceBand,
            Integer questionLimit,
            Integer followUpLimit,
            Integer turnLimit,
            Integer durationLimitMinutes,
            Instant scheduledAt,
            List<ProjectBindingSnapshotResponse> projectBindings,
            Integer knowledgeBindingCount,
            String status,
            Instant startedAt,
            Instant endedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static SessionResponse from(InterviewSessionRecord record) {
            return new SessionResponse(
                    record.getId(),
                    record.getTitle(),
                    record.getTopicMode(),
                    record.getForm(),
                    record.getRound(),
                    record.getInterviewerId(),
                    record.getInterviewerNameSnapshot(),
                    record.getAiConnectionIdSnapshot(),
                    record.getAiModelSnapshot(),
                    record.getWebSearchPolicy(),
                    webSearchOutcome(record),
                    record.getTargetRole(),
                    record.getTargetExperienceBand(),
                    record.getQuestionLimit(),
                    record.getFollowUpLimit(),
                    record.getTurnLimit(),
                    record.getDurationLimitMinutes(),
                    record.getScheduledAt(),
                    parseBindings(record.getProjectBindingsJson()),
                    knowledgeBindingCount(record.getKnowledgeBindingsJson()),
                    record.getStatus() == null ? null : record.getStatus().name(),
                    record.getStartedAt(),
                    record.getEndedAt(),
                    record.getCreatedAt(),
                    record.getUpdatedAt());
        }

        /** 知识卡片快照条数：解析失败或空都按 0，不假装有注入。 */
        private static Integer knowledgeBindingCount(String json) {
            if (json == null || json.isBlank()) {
                return 0;
            }
            try {
                JsonNode nodes = SNAPSHOT_MAPPER.readTree(json);
                return nodes.isArray() ? nodes.size() : 0;
            } catch (Exception ignored) {
                return 0;
            }
        }

        /** 请求档位、当次用的形状、实际是否联网与原因一起回给界面，前端不再自己拼。 */
        private static WebSearchOutcomeResponse webSearchOutcome(InterviewSessionRecord record) {
            if (record.getWebSearchApplied() == null && record.getWebSearchPolicy() == null) {
                return null;
            }
            return new WebSearchOutcomeResponse(record.getWebSearchPolicy(),
                    record.getWebSearchDialect(), record.getWebSearchApplied(), record.getWebSearchNote());
        }

        private static List<ProjectBindingSnapshotResponse> parseBindings(String json) {
            if (json == null || json.isBlank()) {
                return List.of();
            }
            try {
                List<BindingSnapshotReader> entries = SNAPSHOT_MAPPER.readValue(
                        json, BINDING_LIST_TYPE);
                return entries.stream()
                        .map(binding -> new ProjectBindingSnapshotResponse(
                                binding.projectId,
                                binding.projectName,
                                binding.versionId,
                                binding.versionNumber,
                                binding.facts == null ? 0 : binding.facts.size()))
                        .toList();
            } catch (Exception ignored) {
                return List.of();
            }
        }
    }

    /** 只读取快照展示所需字段，忽略未知字段，保证旧数据向前兼容。 */
    static final class BindingSnapshotReader {
        public Long projectId;
        public String projectName;
        public Long versionId;
        public Integer versionNumber;
        public List<Object> facts;
    }

    private static final com.fasterxml.jackson.core.type.TypeReference<List<BindingSnapshotReader>>
            BINDING_LIST_TYPE = new com.fasterxml.jackson.core.type.TypeReference<>() {};

    private static final com.fasterxml.jackson.databind.ObjectMapper SNAPSHOT_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper()
                    .configure(
                            com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                            false);

    public record TurnResponse(
            Long id,
            Integer turnNo,
            String turnType,
            String question,
            String answer,
            String answerSource,
            Instant createdAt,
            Instant answeredAt
    ) {
        public static TurnResponse from(InterviewTurnRecord record) {
            return new TurnResponse(
                    record.getId(),
                    record.getTurnNo(),
                    record.getTurnType() == null ? null : record.getTurnType().name(),
                    record.getQuestion(),
                    record.getAnswer(),
                    record.getAnswerSource() == null ? null : record.getAnswerSource().name(),
                    record.getCreatedAt(),
                    record.getAnsweredAt());
        }
    }

    public record SessionDetailResponse(SessionResponse session, List<TurnResponse> turns) {}

    public record GenerateReportRequest(Long connectionId) {}

    public record ReportResponse(
            Long id,
            Long sessionId,
            String status,
            Integer totalScore,
            String dimensionScoresJson,
            String hiringRecommendation,
            String strengthsJson,
            String weaknessesJson,
            String followUpFindingsJson,
            String projectMasteryJson,
            String knowledgeGapsJson,
            String studySuggestionsJson,
            String failureReason,
            Integer retryCount,
            Instant generatedAt
    ) {
        public static ReportResponse from(InterviewReportRecord record) {
            return new ReportResponse(
                    record.getId(),
                    record.getSessionId(),
                    record.getStatus() == null ? null : record.getStatus().name(),
                    record.getTotalScore(),
                    record.getDimensionScoresJson(),
                    record.getHiringRecommendation(),
                    record.getStrengthsJson(),
                    record.getWeaknessesJson(),
                    record.getFollowUpFindingsJson(),
                    record.getProjectMasteryJson(),
                    record.getKnowledgeGapsJson(),
                    record.getStudySuggestionsJson(),
                    record.getFailureReason(),
                    record.getRetryCount(),
                    record.getGeneratedAt());
        }
    }

    public record ReportStateResponse(boolean exists, ReportResponse report) {
        public static ReportStateResponse empty() {
            return new ReportStateResponse(false, null);
        }
    }
}
