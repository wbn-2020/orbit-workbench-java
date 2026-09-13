package com.orbitworkbench.notification.application;

import com.orbitworkbench.aiconnection.infrastructure.mapper.AiScenarioMapper;
import com.orbitworkbench.notification.infrastructure.mapper.NotificationMapper;
import com.orbitworkbench.preference.domain.UserPreferenceRecord;
import com.orbitworkbench.preference.infrastructure.mapper.UserPreferenceMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 数据保留策略清理（V43，借鉴 EvoFlow data_retention 配置与执行分离）。
 *
 * <p>AGENTS.md 安全边界要求模型请求/响应落库须有「保留周期」；此前只有脱敏、截断、
 * 与用户主动删除，缺周期。这里把它做成按用户可配的策略而非全局硬编码：
 * **默认永久保留**（两列 NULL），用户显式配置多少天才删——绝不悄悄删除用户数据。
 *
 * <p>只清理「已结算」的审计行（RUNNING 行还没有结果，删了会留下无法收尾的孤儿），
 * 以及按创建时间过期的通知。清理是幂等且可重复的：同一 cutoff 再跑一次删 0 行。
 */
@Component
public class RetentionScanner {

    private static final Logger log = LoggerFactory.getLogger(RetentionScanner.class);

    private final UserPreferenceMapper preferenceMapper;
    private final AiScenarioMapper aiScenarioMapper;
    private final NotificationMapper notificationMapper;

    public RetentionScanner(UserPreferenceMapper preferenceMapper,
                            AiScenarioMapper aiScenarioMapper,
                            NotificationMapper notificationMapper) {
        this.preferenceMapper = preferenceMapper;
        this.aiScenarioMapper = aiScenarioMapper;
        this.notificationMapper = notificationMapper;
    }

    /** 每天凌晨 4 点（业务低峰）执行；与通知扫描错开。 */
    @Scheduled(cron = "${orbit.retention.cron:0 0 4 * * *}")
    public void scheduledScan() {
        RetentionResult result = scan(Instant.now());
        if (result.auditsDeleted() > 0 || result.notificationsDeleted() > 0) {
            log.info("保留策略清理完成：审计 {} 行、通知 {} 行，覆盖 {} 个用户",
                    result.auditsDeleted(), result.notificationsDeleted(), result.usersProcessed());
        }
    }

    public RetentionResult scan(Instant now) {
        List<UserPreferenceRecord> policies = preferenceMapper.listRetentionPolicies();
        long audits = 0;
        long notifications = 0;
        for (UserPreferenceRecord policy : policies) {
            Integer auditDays = policy.getAuditRetentionDays();
            if (auditDays != null && auditDays > 0) {
                audits += aiScenarioMapper.purgeAuditsBefore(
                        policy.getUserId(), now.minus(auditDays, ChronoUnit.DAYS));
            }
            Integer notificationDays = policy.getNotificationRetentionDays();
            if (notificationDays != null && notificationDays > 0) {
                notifications += notificationMapper.purgeBefore(
                        policy.getUserId(), now.minus(notificationDays, ChronoUnit.DAYS));
            }
        }
        return new RetentionResult(policies.size(), audits, notifications);
    }

    public record RetentionResult(int usersProcessed, long auditsDeleted, long notificationsDeleted) {
    }
}
