package com.orbitworkbench.notification.application;

import com.orbitworkbench.notification.domain.NotificationEvent;
import com.orbitworkbench.project.application.ProjectVersionImportedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 导入部分文件未解析时补发通知。使用 AFTER_COMMIT，确保只在导入事务真正提交后才产生通知，
 * 事务回滚不会留下幽灵通知。
 */
@Component
public class ProjectImportNotificationListener {

    private final NotificationService notificationService;

    public ProjectImportNotificationListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVersionImported(ProjectVersionImportedEvent event) {
        if (event.failedCount() <= 0) {
            return;
        }
        notificationService.notify(
                NotificationEvent.PROJECT_IMPORT_PARTIAL,
                event.userId(),
                "项目导入有文件未解析",
                "本次导入有 " + event.failedCount() + " 个文件未能解析并已跳过，可在项目详情查看原因。",
                NotificationService.RESOURCE_PROJECT_VERSION,
                event.versionId(),
                "/projects/" + event.projectId(),
                "PROJECT_IMPORT_PARTIAL:" + event.versionId());
    }
}
