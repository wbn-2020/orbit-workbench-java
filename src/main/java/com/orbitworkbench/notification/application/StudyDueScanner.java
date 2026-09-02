package com.orbitworkbench.notification.application;

import com.orbitworkbench.notification.domain.NotificationEvent;
import com.orbitworkbench.preference.application.PreferenceService;
import com.orbitworkbench.studyplan.domain.StudyTaskRecord;
import com.orbitworkbench.studyplan.infrastructure.mapper.StudyTaskMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 复习到期扫描：每天按用户时区为“到期或已逾期且仍处于活动状态”的复习任务补发一条通知。
 * 幂等键按 (任务, 该任务的 due_date) 生成——同一到期日只会产生一条通知；
 * 用户顺延任务后 due_date 变化，才会针对新到期日再次提醒。
 */
@Component
public class StudyDueScanner {

    private static final Logger log = LoggerFactory.getLogger(StudyDueScanner.class);

    private final StudyTaskMapper studyTaskMapper;
    private final PreferenceService preferenceService;
    private final NotificationService notificationService;

    public StudyDueScanner(StudyTaskMapper studyTaskMapper,
                           PreferenceService preferenceService,
                           NotificationService notificationService) {
        this.studyTaskMapper = studyTaskMapper;
        this.preferenceService = preferenceService;
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "${orbit.notification.study-due-cron:0 0 9 * * *}")
    public void scheduledScan() {
        int processed = scan(Instant.now());
        log.info("复习到期通知扫描完成，处理 {} 条", processed);
    }

    /**
     * 查询上界取 UTC 日期后一天，覆盖所有合法 IANA 时区的“本地今天”；
     * 取数后仍按每个用户的时区过滤，避免把其他时区的明日任务提前通知。
     */
    public int scan(Instant now) {
        LocalDate throughDate = now.atZone(ZoneOffset.UTC).toLocalDate().plusDays(1);
        Map<Long, ZoneId> timezones = new HashMap<>();
        int processed = 0;
        for (StudyTaskRecord task : studyTaskMapper.listDueActive(throughDate)) {
            ZoneId timezone = timezones.computeIfAbsent(
                    task.getUserId(), preferenceService::timezone);
            LocalDate localToday = now.atZone(timezone).toLocalDate();
            if (task.getDueDate() == null || task.getDueDate().isAfter(localToday)) {
                continue;
            }
            notificationService.notify(
                    NotificationEvent.STUDY_TASK_DUE,
                    task.getUserId(),
                    "复习任务到期",
                    "「" + task.getTitle() + "」已到期，建议今天完成。",
                    NotificationService.RESOURCE_STUDY_TASK,
                    task.getId(),
                    "/study-plan",
                    "STUDY_TASK_DUE:" + task.getId() + ":" + task.getDueDate());
            processed += 1;
        }
        return processed;
    }
}
