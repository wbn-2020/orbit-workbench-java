package com.orbitworkbench.craft.api;

import com.orbitworkbench.craft.domain.CraftNoteRecord;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** 可复用「本事」接口结构。 */
public final class CraftDtos {

    /**
     * V50 练熟阈值：完成 1 次练习任务即练熟。V49 的按标题幂等保证同一套路
     * 只有一个练习任务，多次「练熟巩固」靠重新安排任务，所以阈值取 1；
     * practice_count 仍继续累加，把「练过几次」如实展示出来。
     */
    static final int MASTERED_THRESHOLD = 1;

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
            /** V49：是否已有对应练习任务（由服务层按 study_task 派生）。 */
            boolean practiced,
            /** V50：完成练习任务的次数（回写自 study_task complete）。 */
            int practiceCount,
            /** V50：最近一次练熟时间。 */
            Instant lastPracticedAt,
            /** V50：已练熟 = 至少完成过一次练习任务（阈值与 V49 同源，从数据派生不单独存储）。 */
            boolean mastered,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static CraftNoteResponse from(CraftNoteRecord record, List<String> tags) {
            return from(record, tags, false);
        }

        public static CraftNoteResponse from(CraftNoteRecord record, List<String> tags,
                                             boolean practiced) {
            return new CraftNoteResponse(record.getId(), record.getCategory(), record.getTitle(),
                    record.getWhenToUse(), record.getContent(), tags, record.getSource().name(),
                    record.getConfirmationStatus().name(), record.getConfidence(),
                    record.isPinned(), practiced, record.getPracticeCount(),
                    record.getLastPracticedAt(), record.getPracticeCount() >= MASTERED_THRESHOLD,
                    record.getCreatedAt(), record.getUpdatedAt());
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
