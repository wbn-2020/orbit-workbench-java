package com.orbitworkbench.worklog.api;

import com.orbitworkbench.worklog.domain.KnowledgeCardRow;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** 知识卡片的请求与响应结构（v2 工作沉淀域）。 */
public final class KnowledgeCardDtos {

    private KnowledgeCardDtos() {
    }

    public record UpdateKnowledgeCardRequest(
            @NotBlank @Size(max = 255) String title,
            @NotBlank @Size(max = 2000) String summary,
            @Size(max = 8) List<String> tags
    ) {}

    public record KnowledgeCardResponse(
            String id,
            String title,
            String summary,
            String sourceLogId,
            List<String> tags,
            String createdAt,
            Integer reviewStage,
            String nextReviewDate,
            String lastReviewedAt
    ) {
        public static KnowledgeCardResponse from(KnowledgeCardRow row, List<String> tags) {
            return new KnowledgeCardResponse(
                    String.valueOf(row.getId()),
                    row.getTitle(),
                    row.getSummary(),
                    row.getSourceLogId() == null ? null : String.valueOf(row.getSourceLogId()),
                    tags,
                    DateTimeFormatter.ISO_INSTANT.format(row.getCreatedAt()),
                    row.getReviewStage(),
                    row.getNextReviewDate() == null ? null : row.getNextReviewDate().toString(),
                    row.getLastReviewedAt() == null ? null : DateTimeFormatter.ISO_INSTANT.format(row.getLastReviewedAt()));
        }
    }

    public record DueCardResponse(
            String id,
            String title,
            String summary,
            List<String> tags,
            String nextReviewDate,
            int overdueDays,
            int reviewStage
    ) {}

    public record DueListResponse(List<DueCardResponse> cards) {}

    public record ReviewResponse(
            String id,
            int reviewStage,
            String nextReviewDate
    ) {}
}
