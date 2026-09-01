package com.orbitworkbench.practice.api;

import com.orbitworkbench.practice.domain.MasteryStatus;
import com.orbitworkbench.practice.domain.PracticeAttemptRecord;
import com.orbitworkbench.practice.domain.PracticeItemRow;
import com.orbitworkbench.practice.domain.PracticeResult;
import com.orbitworkbench.practice.domain.PracticeSource;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** 错题本（C-03）的请求与响应结构，口径见 `15_错题本与专项复练设计.md` §7。 */
public final class PracticeDtos {

    private PracticeDtos() {
    }

    public record CreateItemRequest(
            @NotBlank @Size(max = 128) String topic,
            @NotBlank @Size(max = 4000) String question,
            @Size(max = 4000) String referenceAnswer
    ) {}

    /**
     * 重练作答。自评在 0-100 之间（V28 CHECK），但只有 {@code PASSED} 才要求必填：
     * 未答上来时用户往往给不出分数，强制填会逼人乱填。
     */
    public record AttemptRequest(
            @NotBlank @Size(max = 8000) String answer,
            @NotBlank @Pattern(regexp = "RETRY|PARTIAL|PASSED") String result,
            @Min(0) @Max(100) Integer selfScore,
            @Size(max = 512) String feedback
    ) {}

    /** 用户维护的归类信息；{@code expectedUpdatedAt} 为乐观锁凭据，与简历草稿同口径。 */
    public record ClassificationRequest(
            @NotBlank @Size(max = 128) String topic,
            @Size(max = 4000) String referenceAnswer,
            LocalDate nextReviewDate,
            @NotBlank String expectedUpdatedAt
    ) {}

    public record AttemptResponse(
            Long id,
            String answer,
            Integer selfScore,
            PracticeResult result,
            String feedback,
            Instant attemptedAt
    ) {
        public static AttemptResponse from(PracticeAttemptRecord record) {
            return new AttemptResponse(record.getId(), record.getAnswer(), record.getSelfScore(),
                    record.getResult(), record.getFeedback(), record.getAttemptedAt());
        }
    }

    public record ItemResponse(
            Long itemId,
            PracticeSource sourceType,
            Long sourceId,
            String topic,
            String question,
            String referenceAnswer,
            MasteryStatus masteryStatus,
            LocalDate nextReviewDate,
            boolean archived,
            Instant createdAt,
            Instant updatedAt,
            long attemptCount,
            Instant lastAttemptAt,
            PracticeResult lastResult,
            Integer lastSelfScore,
            int consecutivePassed,
            Long sourceSessionId,
            String sourceSessionTitle,
            String sourceTopicMode,
            String sourceForm,
            Integer reportTotalScore,
            String reportScoringRuleVersion,
            String originalQuestion,
            String originalAnswer,
            String originalAnswerSource,
            String originalTurnType,
            boolean traceableToQuestion,
            /** 报告清单条目追不到原始问题（`15` §5.1），界面据此显示限制而不是留白。 */
            String traceLimitation
    ) {
        public static ItemResponse from(PracticeItemRow row, int consecutivePassed) {
            boolean turnSourced = row.getSourceType() == PracticeSource.INTERVIEW_TURN;
            boolean traceable = turnSourced && row.getOriginalQuestion() != null;
            String limitation = switch (row.getSourceType()) {
                case REPORT -> "来自报告的薄弱清单：报告没有记录该条目对应的问题、回答与维度，只能追到这场面试的报告。";
                case INTERVIEW_TURN -> traceable ? null : "原轮次记录已不存在，无法再追溯原始问答。";
                case MANUAL -> "手工新增，没有系统来源。";
            };
            return new ItemResponse(
                    row.getItemId(), row.getSourceType(), row.getSourceId(), row.getTopic(),
                    row.getQuestion(), row.getReferenceAnswer(), row.getMasteryStatus(),
                    row.getNextReviewDate(), row.isArchived(), row.getCreatedAt(), row.getUpdatedAt(),
                    row.getAttemptCount() == null ? 0L : row.getAttemptCount(),
                    row.getLastAttemptAt(), row.getLastResult(), row.getLastSelfScore(),
                    consecutivePassed, row.getSourceSessionId(), row.getSourceSessionTitle(),
                    row.getSourceTopicMode(), row.getSourceForm(), row.getReportTotalScore(),
                    row.getReportScoringRuleVersion(), row.getOriginalQuestion(), row.getOriginalAnswer(),
                    row.getOriginalAnswerSource(), row.getOriginalTurnType(), traceable, limitation);
        }
    }

    public record ItemListResponse(
            List<ItemResponse> items,
            long total,
            int page,
            int size
    ) {}

    public record ItemDetailResponse(ItemResponse item, List<AttemptResponse> attempts) {}

    public record ImportResponse(
            Long sessionId,
            String sessionTitle,
            Long reportId,
            int created,
            int skipped,
            List<String> topics,
            String note
    ) {}

    public record TopicCount(String topic, long itemCount, long notMasteredCount) {}

    public record PracticeSummaryResponse(
            long total,
            long newCount,
            long learningCount,
            long masteredCount,
            /** 一条都没有时不渲染掌握进度（与 `14` §6 同口径）。 */
            boolean renderable,
            List<TopicCount> topics,
            Instant lastAttemptAt
    ) {}
}
