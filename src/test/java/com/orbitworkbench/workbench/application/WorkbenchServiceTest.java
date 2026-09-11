package com.orbitworkbench.workbench.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orbitworkbench.preference.application.PreferenceService;
import com.orbitworkbench.schedule.api.ScheduleDtos.AgendaItemResponse;
import com.orbitworkbench.schedule.application.ScheduleService;
import com.orbitworkbench.workbench.infrastructure.mapper.WorkbenchMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkbenchServiceTest {

    @Mock
    private WorkbenchMapper mapper;
    @Mock
    private ScheduleService scheduleService;
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
                mapper, scheduleService, preferenceService, Clock.fixed(fixedNow, ZoneId.of("UTC")));

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
                mapper, scheduleService, preferenceService, Clock.fixed(fixedNow, ZoneId.of("UTC")));

        var summary = service.summary(1L);

        assertEquals(25, summary.focusTodayMinutes());
        verify(mapper).focusMinutesToday(
                1L,
                Instant.parse("2026-03-08T05:00:00Z"),
                Instant.parse("2026-03-09T04:00:00Z"));
    }

    @Test
    void summaryMapsAgendaFromScheduleModule() {
        ZoneId utc = ZoneId.of("UTC");
        Instant fixedNow = Instant.parse("2026-09-07T09:00:00Z");
        when(preferenceService.timezone(1L)).thenReturn(utc);
        when(scheduleService.agenda(1L,
                Instant.parse("2026-09-07T00:00:00Z"),
                Instant.parse("2026-09-08T00:00:00Z")))
                .thenReturn(List.of(
                        new AgendaItemResponse("INTERVIEW", 7L, "模拟面试",
                                Instant.parse("2026-09-07T14:30:00Z"), null, false, "PLANNED", "/interviews/7"),
                        new AgendaItemResponse("STUDY_TASK", 9L, "复习 HashMap",
                                Instant.parse("2026-09-07T00:00:00Z"), null, true, "COMPLETED", "/study-plan"),
                        new AgendaItemResponse("CUSTOM", 11L, "周会",
                                Instant.parse("2026-09-07T10:00:00Z"), null, false, "CANCELLED", null)));

        WorkbenchService service = new WorkbenchService(
                mapper, scheduleService, preferenceService, Clock.fixed(fixedNow, utc));

        var summary = service.summary(1L);

        assertEquals(3, summary.agenda().size());
        assertEquals("INTERVIEW:7", summary.agenda().get(0).id());
        assertEquals("14:30", summary.agenda().get(0).time());
        assertEquals("模拟面试", summary.agenda().get(0).title());
        assertEquals(false, summary.agenda().get(0).done());
        assertEquals("全天", summary.agenda().get(1).time());
        assertEquals(true, summary.agenda().get(1).done());
        assertEquals("10:00", summary.agenda().get(2).time());
    }
}
