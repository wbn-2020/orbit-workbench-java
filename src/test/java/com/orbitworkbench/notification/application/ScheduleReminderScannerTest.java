package com.orbitworkbench.notification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orbitworkbench.notification.domain.NotificationEvent;
import com.orbitworkbench.preference.application.PreferenceService;
import com.orbitworkbench.schedule.domain.ScheduleEventRecord;
import com.orbitworkbench.schedule.domain.ScheduleSourceType;
import com.orbitworkbench.schedule.domain.ScheduleStatus;
import com.orbitworkbench.schedule.infrastructure.mapper.ScheduleEventMapper;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduleReminderScannerTest {

    @Mock
    private ScheduleEventMapper scheduleEventMapper;

    @Mock
    private PreferenceService preferenceService;

    @Mock
    private NotificationService notificationService;

    @Test
    void scanNotifiesDueEventsWithUserTimezoneAndStableKey() {
        Instant now = Instant.parse("2026-09-02T01:00:00Z");
        ScheduleEventRecord event = event(7L, 3L, "技术分享会",
                Instant.parse("2026-09-02T09:30:00Z"), 30);
        when(scheduleEventMapper.listDueReminders(now)).thenReturn(List.of(event));
        when(preferenceService.timezone(3L)).thenReturn(ZoneId.of("Asia/Shanghai"));

        ScheduleReminderScanner scanner = new ScheduleReminderScanner(
                scheduleEventMapper, preferenceService, notificationService);

        assertEquals(1, scanner.scan(now));
        verify(notificationService).notify(
                eq(NotificationEvent.SCHEDULE_REMINDER),
                eq(3L),
                eq("日程提醒"),
                eq("「技术分享会」将在 30 分钟后开始（9月2日 17:30）。"),
                eq(NotificationService.RESOURCE_SCHEDULE_EVENT),
                eq(7L),
                eq("/schedule"),
                eq("SCHEDULE_REMINDER:7:2026-09-02T09:30:00Z:30"));
    }

    @Test
    void scanWithNoDueEventsDoesNotNotify() {
        Instant now = Instant.parse("2026-09-02T01:00:00Z");
        when(scheduleEventMapper.listDueReminders(now)).thenReturn(List.of());

        ScheduleReminderScanner scanner = new ScheduleReminderScanner(
                scheduleEventMapper, preferenceService, notificationService);

        assertEquals(0, scanner.scan(now));
        verify(notificationService, times(0)).notify(
                any(), any(), anyString(), anyString(), anyString(), any(), anyString(), anyString());
    }

    private static ScheduleEventRecord event(Long id, Long userId, String title,
                                             Instant startAt, int reminderMinutes) {
        ScheduleEventRecord event = new ScheduleEventRecord();
        event.setId(id);
        event.setUserId(userId);
        event.setSourceType(ScheduleSourceType.CUSTOM);
        event.setTitle(title);
        event.setStartAt(startAt);
        event.setStatus(ScheduleStatus.PLANNED);
        event.setReminderMinutes(reminderMinutes);
        return event;
    }
}
