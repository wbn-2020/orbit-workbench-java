package com.orbitworkbench.userfact.api;

import com.orbitworkbench.userfact.domain.UserFactRecord;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** 用户画像事实（个人记忆层）接口结构。 */
public final class UserFactDtos {

    private UserFactDtos() {
    }

    public record UserFactResponse(
            Long id,
            String factType,
            String title,
            String content,
            String source,
            String status,
            Integer confidence,
            Instant confirmedAt,
            Instant lastSeenAt,
            /** 距上次确认是否已超过时效阈值（后端判定，前端不重复实现规则）。 */
            boolean stale,
            /** 距上次确认的天数；从未确认（候选池）为 null。 */
            Long staleDays,
            String archivedReason,
            Instant createdAt
    ) {
        public static UserFactResponse from(UserFactRecord record) {
            return from(record, UserFactFreshness.STALE_AFTER_DAYS);
        }

        public static UserFactResponse from(UserFactRecord record, long staleAfterDays) {
            Instant lastSeen = UserFactFreshness.lastSeen(record);
            Long days = lastSeen == null ? null
                    : UserFactFreshness.daysSince(lastSeen);
            boolean stale = days != null && days >= staleAfterDays;
            return new UserFactResponse(record.getId(), record.getFactType(), record.getTitle(),
                    record.getContent(), record.getSource().name(), record.getConfirmationStatus().name(),
                    record.getConfidence(), record.getConfirmedAt(), lastSeen, stale, days,
                    record.getArchivedReason(), record.getCreatedAt());
        }
    }

    /** 确认请求：允许在确认前修正类型/标题/内容（模型输出只是草稿）。 */
    public record ConfirmUserFactRequest(
            @NotBlank @Size(max = 24) String factType,
            @NotBlank @Size(max = 120) String title,
            @NotBlank @Size(max = 800) String content
    ) {}

    public record CreateUserFactRequest(
            @NotBlank @Size(max = 24) String factType,
            @NotBlank @Size(max = 120) String title,
            @NotBlank @Size(max = 800) String content,
            Integer confidence
    ) {}

    public record UserFactListResponse(List<UserFactResponse> items) {}

    public record DistillResultResponse(List<UserFactResponse> suggestions, int skippedLowConfidence) {}
}
