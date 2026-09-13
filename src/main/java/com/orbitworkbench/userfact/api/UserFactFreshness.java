package com.orbitworkbench.userfact.api;

import com.orbitworkbench.userfact.domain.UserFactRecord;
import java.time.Duration;
import java.time.Instant;

/**
 * 记忆时效判定（V43）。
 *
 * <p>「时效」只对已确认事实有意义：候选池还没被采纳，已归档不再注入。
 * 判定基准是 {@code lastSeenAt}（用户最后一次确认它仍然成立），
 * 缺失时回退到 {@code confirmedAt}——存量数据在 V43 已回填，这里的回退只防御意外。
 *
 * <p>阈值只此一处定义：Service 与 DTO 共用，避免前后端各写一套天数规则而慢慢漂移。
 */
public final class UserFactFreshness {

    /** 超过这个天数未再确认，即视为「可能过期」，注入时加标注、界面提示复查。 */
    public static final long STALE_AFTER_DAYS = 90;

    private UserFactFreshness() {
    }

    /** 判定基准时间；候选池（无确认时刻）返回 null，表示「谈不上时效」。 */
    public static Instant lastSeen(UserFactRecord record) {
        if (record == null) {
            return null;
        }
        if (record.getLastSeenAt() != null) {
            return record.getLastSeenAt();
        }
        return record.getConfirmedAt();
    }

    /** 距今整日数（向下取整，可为 0）；基准缺失返回 null。 */
    public static Long daysSince(Instant from) {
        if (from == null) {
            return null;
        }
        return Duration.between(from, Instant.now()).toDays();
    }

    /** 是否已过时效阈值。 */
    public static boolean isStale(UserFactRecord record, long staleAfterDays) {
        Long days = daysSince(lastSeen(record));
        return days != null && days >= staleAfterDays;
    }

    /**
     * 注入用的时效标注：陈旧时返回「（N 个月前确认，可能已过时）」这类短语，
     * 新鲜或无法判定时返回空串。让模型知道这条情报的成色，而不是把它当今日事实用。
     */
    public static String injectNote(UserFactRecord record) {
        Long days = daysSince(lastSeen(record));
        if (days == null || days < STALE_AFTER_DAYS) {
            return "";
        }
        return "（约 " + describeAge(days) + "前确认，可能已过时）";
    }

    /** 天数转成人话：<30 天给「N 天」、<365 给「N 个月」、否则「N 年」。 */
    public static String describeAge(long days) {
        if (days < 30) {
            return days + " 天";
        }
        if (days < 365) {
            return Math.round(days / 30.0) + " 个月";
        }
        return Math.round(days / 365.0) + " 年";
    }
}
