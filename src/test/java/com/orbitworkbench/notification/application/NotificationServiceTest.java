package com.orbitworkbench.notification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orbitworkbench.notification.api.NotificationDtos.NotificationResponse;
import com.orbitworkbench.notification.domain.NotificationEvent;
import com.orbitworkbench.notification.domain.NotificationRecord;
import com.orbitworkbench.notification.infrastructure.mapper.NotificationMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.PageResult;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationMapper mapper;

    @InjectMocks
    private NotificationService service;

    @Test
    void listReturnsPagedResponsesWithDerivedReadFlag() {
        when(mapper.listByUser(1L, false, 20, 0)).thenReturn(List.of(record(10L, null)));
        when(mapper.countByUser(1L, false)).thenReturn(42);

        PageResult<NotificationResponse> page = service.list(1L, false, 1, 20);

        assertEquals(42, page.total());
        assertEquals(1, page.items().size());
        assertFalse(page.items().get(0).read());
    }

    @Test
    void listClampsPageSizeToMaximum() {
        when(mapper.listByUser(eq(1L), eq(true), eq(50), eq(50))).thenReturn(List.of(record(1L, Instant.now())));
        when(mapper.countByUser(1L, true)).thenReturn(1);

        PageResult<NotificationResponse> page = service.list(1L, true, 2, 999);

        assertTrue(page.items().get(0).read());
        verify(mapper).listByUser(1L, true, 50, 50);
    }

    @Test
    void unreadCountQueriesOnlyUnread() {
        when(mapper.countByUser(1L, true)).thenReturn(3);
        assertEquals(3, service.unreadCount(1L));
    }

    @Test
    void markReadThrowsWhenNotificationNotOwned() {
        when(mapper.findByIdAndUser(9L, 1L)).thenReturn(null);

        assertThrows(ApiException.class, () -> service.markRead(1L, 9L));
        verify(mapper, never()).markRead(any(), any(), any());
    }

    @Test
    void markReadSetsTimestampForOwnedNotification() {
        when(mapper.findByIdAndUser(9L, 1L)).thenReturn(record(9L, null));

        service.markRead(1L, 9L);

        verify(mapper).markRead(eq(9L), eq(1L), any(Instant.class));
    }

    @Test
    void markAllReadReturnsUpdatedRows() {
        when(mapper.markAllRead(eq(1L), any(Instant.class))).thenReturn(5);
        assertEquals(5, service.markAllRead(1L));
    }

    @Test
    void notifyTruncatesTitleAndContentToColumnLimits() {
        service.notify(NotificationEvent.INTERVIEW_REPORT_READY, 1L,
                "长".repeat(200), "多".repeat(600),
                NotificationService.RESOURCE_INTERVIEW_SESSION, 4L, "/interviews/4/report",
                "INTERVIEW_REPORT_READY:4");

        ArgumentCaptor<NotificationRecord> captor = ArgumentCaptor.forClass(NotificationRecord.class);
        verify(mapper).insertIgnore(captor.capture());
        NotificationRecord saved = captor.getValue();
        assertEquals(128, saved.getTitle().length());
        assertEquals(512, saved.getContent().length());
        assertEquals(NotificationEvent.INTERVIEW_REPORT_READY, saved.getEventType());
    }

    @Test
    void notifySwallowsMapperFailureSoBusinessFlowContinues() {
        when(mapper.insertIgnore(any())).thenThrow(new RuntimeException("db down"));

        service.notify(NotificationEvent.STUDY_TASK_DUE, 1L, "复习任务到期", "x",
                NotificationService.RESOURCE_STUDY_TASK, 2L, "/study-plan", "STUDY_TASK_DUE:2:2026-08-30");
        // 不抛出即通过：通知写失败不得冒泡到业务调用。
    }

    private static NotificationRecord record(Long id, Instant readAt) {
        NotificationRecord record = new NotificationRecord();
        record.setId(id);
        record.setUserId(1L);
        record.setEventType(NotificationEvent.INTERVIEW_REPORT_READY);
        record.setTitle("报告已生成");
        record.setContent("点击查看");
        record.setResourceType(NotificationService.RESOURCE_INTERVIEW_SESSION);
        record.setResourceId(4L);
        record.setResourceRoute("/interviews/4/report");
        record.setIdempotencyKey("INTERVIEW_REPORT_READY:4");
        record.setReadAt(readAt);
        record.setCreatedAt(Instant.parse("2026-08-30T01:00:00Z"));
        return record;
    }
}
