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
            Long projectVersionId
    ) {}

    public record SourceItem(String relativePath, int chunkNo, String snippet) {}

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
