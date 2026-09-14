package com.orbitworkbench.craft.api;

import com.orbitworkbench.craft.domain.CraftNoteRecord;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** 可复用「本事」接口结构。 */
public final class CraftDtos {

    private CraftDtos() {
    }

    public record CraftNoteResponse(
            Long id,
            String category,
            String title,
            String whenToUse,
            String content,
            List<String> tags,
            String source,
            String status,
            Integer confidence,
            boolean pinned,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static CraftNoteResponse from(CraftNoteRecord record, List<String> tags) {
            return new CraftNoteResponse(record.getId(), record.getCategory(), record.getTitle(),
                    record.getWhenToUse(), record.getContent(), tags, record.getSource().name(),
                    record.getConfirmationStatus().name(), record.getConfidence(),
                    record.isPinned(), record.getCreatedAt(), record.getUpdatedAt());
        }
    }

    public record CraftListResponse(List<CraftNoteResponse> items) {}

    public record SaveCraftRequest(
            @NotBlank @Size(max = 24) String category,
            @NotBlank @Size(max = 120) String title,
            @NotBlank @Size(max = 255) String whenToUse,
            @NotBlank @Size(max = 2000) String content,
            List<@Size(max = 64) String> tags
    ) {}

    public record DistillCraftResponse(List<CraftNoteResponse> suggestions) {}

    public record PinCraftRequest(boolean pinned) {}
}
