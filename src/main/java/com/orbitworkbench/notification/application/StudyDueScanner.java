package com.orbitworkbench.notification.application;

import com.orbitworkbench.notification.domain.NotificationEvent;
import com.orbitworkbench.studyplan.domain.StudyTaskRecord;
import com.orbitworkbench.studyplan.infrastructure.mapper.StudyTaskMapper;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 复习到期扫描：每天为“到期或已逾期且仍处于活动状态”的复习任务补发一条通知。
 * 幂等键按 (任务, 该任务的 due_date) 生成——同一到期日只会产生一条通知；
 * 用户顺延任务后 due_date 变化，才会针对新到期日再次提醒。
 */
@Component
public class StudyDueScanner {

    private static final Logger log = LoggerFactory.getLogger(StudyDueScanner.class);

    private final StudyTaskMapper studyTaskMapper;
    private final NotificationService notificationService;

    public StudyDueScanner(StudyTaskMapper studyTaskMapper, NotificationService notificationService) {
        this.studyTaskMapper = studyTaskMapper;
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "${orbit.notification.study-due-cron:0 0 9 * * *}")
    public void scheduledScan() {
        int processed = scan(LocalDate.now());
        log.info("复习到期通知扫描完成，处理 {} 条", processed);
    }

    public int scan(LocalDate today) {
        int processed = 0;
        for (StudyTaskRecord task : studyTaskMapper.listDueActive(today)) {
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
