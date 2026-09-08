package com.orbitworkbench.workbench.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orbitworkbench.preference.application.PreferenceService;
import com.orbitworkbench.workbench.infrastructure.mapper.WorkbenchMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkbenchServiceTest {

    @Mock
    private WorkbenchMapper mapper;
    @Mock
    private PreferenceService preferenceService;

    @Test
    void summaryUsesUserTimezoneForTodayFocusWindow() {
        ZoneId tokyo = ZoneId.of("Asia/Tokyo");
        Instant fixedNow = Instant.parse("2026-09-07T15:30:00Z");
        when(preferenceService.timezone(1L)).thenReturn(tokyo);
        when(mapper.countPendingDistill(1L)).thenReturn(0L);
        when(mapper.countActiveGoals(1L)).thenReturn(0L);
        when(mapper.countWorkLogs(1L)).thenReturn(0L);
        when(mapper.countKnowledgeCards(1L)).thenReturn(0L);
        when(mapper.countLearningGoals(1L)).thenReturn(0L);
        when(mapper.sumFocusMinutesAll(1L)).thenReturn(0L);
        when(mapper.focusMinutesToday(eq(1L),
                eq(Instant.parse("2026-09-07T15:00:00Z")),
                eq(Instant.parse("2026-09-08T15:00:00Z")))).thenReturn(40L);

        WorkbenchService service = new WorkbenchService(
                mapper, preferenceService, Clock.fixed(fixedNow, ZoneId.of("UTC")));

        var summary = service.summary(1L);
        assertEquals(40, summary.focusTodayMinutes());
        assertEquals("/learning-update", summary.assets().get(3).to());
        verify(mapper).focusMinutesToday(
                1L,
                Instant.parse("2026-09-07T15:00:00Z"),
                Instant.parse("2026-09-08T15:00:00Z"));
    }

    @Test
    void summaryUsesNextLocalMidnightAcrossDaylightSavingTransition() {
        ZoneId newYork = ZoneId.of("America/New_York");
        Instant fixedNow = Instant.parse("2026-03-08T16:00:00Z");
        when(preferenceService.timezone(1L)).thenReturn(newYork);
        when(mapper.countPendingDistill(1L)).thenReturn(0L);
        when(mapper.countActiveGoals(1L)).thenReturn(0L);
        when(mapper.countWorkLogs(1L)).thenReturn(0L);
        when(mapper.countKnowledgeCards(1L)).thenReturn(0L);
        when(mapper.countLearningGoals(1L)).thenReturn(0L);
        when(mapper.sumFocusMinutesAll(1L)).thenReturn(0L);
        when(mapper.focusMinutesToday(eq(1L),
                eq(Instant.parse("2026-03-08T05:00:00Z")),
                eq(Instant.parse("2026-03-09T04:00:00Z")))).thenReturn(25L);

        WorkbenchService service = new WorkbenchService(
                mapper, preferenceService, Clock.fixed(fixedNow, ZoneId.of("UTC")));

        var summary = service.summary(1L);

        assertEquals(25, summary.focusTodayMinutes());
        verify(mapper).focusMinutesToday(
                1L,
                Instant.parse("2026-03-08T05:00:00Z"),
                Instant.parse("2026-03-09T04:00:00Z"));
    }
}
