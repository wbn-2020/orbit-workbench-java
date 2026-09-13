package com.orbitworkbench.userfact.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orbitworkbench.userfact.domain.UserFactRecord;
import com.orbitworkbench.userfact.domain.UserFactSource;
import com.orbitworkbench.userfact.domain.UserFactStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

/**
 * 记忆时效判定（V43）。
 *
 * <p>要守住的是两件事：① 判定基准是 last_seen_at，缺失才回退 confirmed_at；
 * ② 陈旧事实的注入标注只在真的陈旧时出现——新鲜事实不能被无端标注成「可能过时」。
 */
class UserFactFreshnessTest {

    private UserFactRecord confirmed(long confirmedDaysAgo, Long lastSeenDaysAgo) {
        UserFactRecord record = new UserFactRecord();
        record.setId(1L);
        record.setUserId(1L);
        record.setFactType("KNOWLEDGE");
        record.setTitle("Java 水平");
        record.setContent("仅具备工作能力");
        record.setSource(UserFactSource.AI_SUGGESTED);
        record.setConfirmationStatus(UserFactStatus.CONFIRMED);
        record.setConfirmedAt(Instant.now().minus(confirmedDaysAgo, ChronoUnit.DAYS));
        if (lastSeenDaysAgo != null) {
            record.setLastSeenAt(Instant.now().minus(lastSeenDaysAgo, ChronoUnit.DAYS));
        }
        return record;
    }

    @Test
    void lastSeenAtIsTheBasisAndOverridesConfirmedAt() {
        // 一年前确认、昨天复查过 —— 按 last_seen_at 看是新鲜的，不能判陈旧
        UserFactRecord fact = confirmed(365, 1L);
        assertFalse(UserFactFreshness.isStale(fact, UserFactFreshness.STALE_AFTER_DAYS));
        assertEquals("", UserFactFreshness.injectNote(fact));
    }

    @Test
    void fallsBackToConfirmedAtWhenLastSeenMissing() {
        UserFactRecord fact = confirmed(200, null);
        assertTrue(UserFactFreshness.isStale(fact, UserFactFreshness.STALE_AFTER_DAYS));
    }

    @Test
    void staleFactGetsHumanReadableNote() {
        UserFactRecord fact = confirmed(200, 200L);
        String note = UserFactFreshness.injectNote(fact);
        assertTrue(note.contains("个月"), "应给出人话时长，实际：" + note);
        assertTrue(note.contains("可能已过时"), "应明确标注可能过时，实际：" + note);
    }

    @Test
    void thresholdBoundaryIsInclusiveAtNinetyDays() {
        // 90 天整算陈旧（>=），89 天不算——边界只此一处定义
        assertTrue(UserFactFreshness.isStale(confirmed(90, 90L), 90));
        assertFalse(UserFactFreshness.isStale(confirmed(89, 89L), 90));
    }

    @Test
    void candidateWithoutConfirmationHasNoFreshness() {
        UserFactRecord analyzed = confirmed(10, null);
        analyzed.setConfirmedAt(null);
        analyzed.setConfirmationStatus(UserFactStatus.ANALYZED);
        assertEquals(null, UserFactFreshness.daysSince(UserFactFreshness.lastSeen(analyzed)));
        assertEquals("", UserFactFreshness.injectNote(analyzed));
    }

    @Test
    void describesAgeInDaysMonthsYears() {
        assertEquals("10 天", UserFactFreshness.describeAge(10));
        assertEquals("3 个月", UserFactFreshness.describeAge(95));
        assertEquals("2 年", UserFactFreshness.describeAge(730));
    }
}
