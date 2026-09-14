package com.orbitworkbench.knowledge.api;

import com.orbitworkbench.knowledge.domain.KnowledgeChunkRecord;
import com.orbitworkbench.knowledge.domain.ProjectFactRecord;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class KnowledgeDtos {
    private KnowledgeDtos() {}

    public record BuildRequest(Long projectVersionId) {}

    public record BuildResultResponse(int chunkCount, int fileCount) {}

    public record AskRequest(
            @NotBlank @Size(max = 500) String question,
            Long projectVersionId,
            /** 检索范围：MATERIALS（项目资料，默认）或 PERSONAL（我的状态，V48）。 */
            @Pattern(regexp = "MATERIALS|PERSONAL", message = "scope 取值不合法") String scope
    ) {}

    /**
     * 回答来源。[category] 仅「我的状态」范围使用（面试报告/画像事实/复习任务/本事库/工作记录/学习目标）；
     * 项目资料范围沿用 relativePath + chunkNo（category 为空，non_null 序列化会省略该键）。
     */
    public record SourceItem(String relativePath, int chunkNo, String snippet, String category) {
        public SourceItem(String relativePath, int chunkNo, String snippet) {
            this(relativePath, chunkNo, snippet, null);
        }
    }

    public record AskResponse(
            String answer,
            boolean insufficient,
            List<SourceItem> sources
    ) {
        public static AskResponse empty() {
            return new AskResponse(null, true, List.of());
        }
    }

    public record FactResponse(
            Long id,
            String factType,
            String title,
            String content,
            String source,
            String confirmationStatus,
            Integer confidence,
            Instant confirmedAt
    ) {
        public static FactResponse from(ProjectFactRecord record) {
            return new FactResponse(
                    record.getId(),
                    record.getFactType(),
                    record.getTitle(),
                    record.getContent(),
                    record.getSource() == null ? null : record.getSource().name(),
                    record.getConfirmationStatus() == null ? null : record.getConfirmationStatus().name(),
                    record.getConfidence(),
                    record.getConfirmedAt());
        }
    }

    public record GenerateFactsRequest(Long connectionId) {}

    public record ConfirmFactRequest(
            @NotBlank @Pattern(regexp = "BUSINESS|STRUCTURE|RISK|RESPONSIBILITY|TECH_STACK|OTHER")
            String factType,
            @NotBlank @Size(max = 255) String title,
            @NotBlank @Size(max = 2000) String content
    ) {}

    public record FactsListResponse(List<FactResponse> facts) {}

    public record ChunkResponse(
            Long id,
            String relativePath,
            int chunkNo,
            String snippet
    ) {
        public static ChunkResponse from(KnowledgeChunkRecord record) {
            String content = record.getContent() == null ? "" : record.getContent();
            return new ChunkResponse(record.getId(), record.getRelativePath(),
                    record.getChunkNo() == null ? 0 : record.getChunkNo(),
                    content.substring(0, Math.min(content.length(), 200)));
        }
    }
}
