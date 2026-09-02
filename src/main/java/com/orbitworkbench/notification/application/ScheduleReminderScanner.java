package com.orbitworkbench.notification.application;

import com.orbitworkbench.notification.domain.NotificationEvent;
import com.orbitworkbench.preference.application.PreferenceService;
import com.orbitworkbench.schedule.domain.ScheduleEventRecord;
import com.orbitworkbench.schedule.infrastructure.mapper.ScheduleEventMapper;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每分钟扫描自定义日程的到点提醒。通知写入仍由 notification 的唯一幂等键兜底，
 * 因此调度器重复执行或多实例执行都不会为同一提醒重复落库。
 */
@Component
public class ScheduleReminderScanner {

    private static final Logger log = LoggerFactory.getLogger(ScheduleReminderScanner.class);
    private static final DateTimeFormatter LOCAL_TIME =
            DateTimeFormatter.ofPattern("M月d日 HH:mm");

    private final ScheduleEventMapper scheduleEventMapper;
    private final PreferenceService preferenceService;
    private final NotificationService notificationService;

    public ScheduleReminderScanner(ScheduleEventMapper scheduleEventMapper,
                                    PreferenceService preferenceService,
                                    NotificationService notificationService) {
        this.scheduleEventMapper = scheduleEventMapper;
        this.preferenceService = preferenceService;
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "${orbit.notification.schedule-reminder-cron:0 * * * * *}")
    public void scheduledScan() {
        int processed = scan(Instant.now());
        log.info("自定义日程提醒扫描完成，处理 {} 条", processed);
    }

    public int scan(Instant now) {
        int processed = 0;
        for (ScheduleEventRecord event : scheduleEventMapper.listDueReminders(now)) {
            ZoneId timezone = preferenceService.timezone(event.getUserId());
            ZonedDateTime localStart = event.getStartAt().atZone(timezone);
            String localTime = LOCAL_TIME.format(localStart);
            int reminderMinutes = event.getReminderMinutes() == null
                    ? 0 : event.getReminderMinutes();
            String lead = reminderMinutes == 0
                    ? "现在开始"
                    : "将在 " + reminderMinutes + " 分钟后开始";
            notificationService.notify(
                    NotificationEvent.SCHEDULE_REMINDER,
                    event.getUserId(),
                    "日程提醒",
                    "「" + event.getTitle() + "」" + lead + "（" + localTime + "）。",
                    NotificationService.RESOURCE_SCHEDULE_EVENT,
                    event.getId(),
                    event.getResourceRoute() == null ? "/schedule" : event.getResourceRoute(),
                    "SCHEDULE_REMINDER:" + event.getId() + ":"
                            + event.getStartAt() + ":" + reminderMinutes);
            processed += 1;
        }
        return processed;
    }
}
