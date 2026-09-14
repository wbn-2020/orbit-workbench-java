package com.orbitworkbench.userfact.api;

import com.orbitworkbench.userfact.domain.UserFactRecord;
import com.orbitworkbench.userfact.domain.UserFactStatus;
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
            /** 被组装进 AI 请求的次数（V46 用量治理）；从未注入为 0。 */
            int injectionCount,
            Instant lastInjectedAt,
            /** 冷记忆：已确认但从未被注入过——用户可据此清理低价值事实。 */
            boolean cold,
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
            int injections = record.getInjectionCount();
            boolean confirmed = record.getConfirmationStatus() == UserFactStatus.CONFIRMED;
            return new UserFactResponse(record.getId(), record.getFactType(), record.getTitle(),
                    record.getContent(), record.getSource().name(), record.getConfirmationStatus().name(),
                    record.getConfidence(), record.getConfirmedAt(), lastSeen, stale, days,
                    injections, record.getLastInjectedAt(), confirmed && injections == 0,
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

    /** 画像编译快照（V44）：stale = 编译后事实集有新增/归档，注入已自动回退逐条模式。 */
    public record DigestResponse(
            Long id,
            String digest,
            int sourceCount,
            String model,
            Instant compiledAt,
            boolean stale,
            int factsAdded,
            int factsRemoved
    ) {}

    /** 近期关注（V46）：expired = 已过失效时刻，不再注入但保留供用户决定去留。 */
    public record FocusNoteResponse(
            Long id,
            String content,
            Instant expiresAt,
            boolean expired,
            Instant updatedAt
    ) {}

    public record SaveFocusNoteRequest(
            @NotBlank @Size(max = 400) String content,
            /** 多少天后自动失效；为空表示不自动失效。 */
            Integer expiresInDays
    ) {}
}
