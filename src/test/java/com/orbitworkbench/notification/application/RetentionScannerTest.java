package com.orbitworkbench.notification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orbitworkbench.aiconnection.infrastructure.mapper.AiScenarioMapper;
import com.orbitworkbench.notification.infrastructure.mapper.NotificationMapper;
import com.orbitworkbench.preference.domain.UserPreferenceRecord;
import com.orbitworkbench.preference.infrastructure.mapper.UserPreferenceMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * 保留策略清理（V43）。核心纪律：**默认永久保留**——没配保留期的用户
 * 一行都不该被删；配了才对各自的窗口做清理。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RetentionScannerTest {

    @Mock
    private UserPreferenceMapper preferenceMapper;
    @Mock
    private AiScenarioMapper aiScenarioMapper;
    @Mock
    private NotificationMapper notificationMapper;

    private RetentionScanner scanner;

    @BeforeEach
    void setUp() {
        scanner = new RetentionScanner(preferenceMapper, aiScenarioMapper, notificationMapper);
    }

    private UserPreferenceRecord policy(Integer auditDays, Integer notificationDays) {
        UserPreferenceRecord record = new UserPreferenceRecord();
        record.setUserId(1L);
        record.setAuditRetentionDays(auditDays);
        record.setNotificationRetentionDays(notificationDays);
        return record;
    }

    @Test
    void noPolicyMeansNothingIsDeleted() {
        when(preferenceMapper.listRetentionPolicies()).thenReturn(List.of());

        RetentionScanner.RetentionResult result = scanner.scan(Instant.now());

        assertEquals(0, result.usersProcessed());
        verify(aiScenarioMapper, never()).purgeAuditsBefore(any(), any());
        verify(notificationMapper, never()).purgeBefore(any(), any());
    }

    @Test
    void nullRetentionOnOneSideLeavesThatTableUntouched() {
        // 只配了审计保留期：通知一行都不能动
        when(preferenceMapper.listRetentionPolicies()).thenReturn(List.of(policy(30, null)));
        when(aiScenarioMapper.purgeAuditsBefore(eq(1L), any())).thenReturn(5);

        RetentionScanner.RetentionResult result = scanner.scan(Instant.now());

        assertEquals(5, result.auditsDeleted());
        assertEquals(0, result.notificationsDeleted());
        verify(notificationMapper, never()).purgeBefore(any(), any());
    }

    @Test
    void cutoffIsNowMinusRetentionDays() {
        when(preferenceMapper.listRetentionPolicies()).thenReturn(List.of(policy(30, 90)));
        Instant now = Instant.parse("2026-09-13T12:00:00Z");

        scanner.scan(now);

        ArgumentCaptor<Instant> auditCutoff = ArgumentCaptor.forClass(Instant.class);
        verify(aiScenarioMapper).purgeAuditsBefore(eq(1L), auditCutoff.capture());
        assertEquals(now.minus(30, ChronoUnit.DAYS), auditCutoff.getValue());

        ArgumentCaptor<Instant> notifCutoff = ArgumentCaptor.forClass(Instant.class);
        verify(notificationMapper).purgeBefore(eq(1L), notifCutoff.capture());
        assertEquals(now.minus(90, ChronoUnit.DAYS), notifCutoff.getValue());
    }

    @Test
    void aggregatesAcrossUsers() {
        when(preferenceMapper.listRetentionPolicies())
                .thenReturn(List.of(policy(30, 30), policy(60, null)));
        when(aiScenarioMapper.purgeAuditsBefore(any(), any())).thenReturn(2);
        when(notificationMapper.purgeBefore(any(), any())).thenReturn(3);

        RetentionScanner.RetentionResult result = scanner.scan(Instant.now());

        assertEquals(2, result.usersProcessed());
        assertEquals(4, result.auditsDeleted());
        assertEquals(3, result.notificationsDeleted());
    }
}
