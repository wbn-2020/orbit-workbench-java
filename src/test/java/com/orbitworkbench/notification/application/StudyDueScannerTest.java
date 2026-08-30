package com.orbitworkbench.notification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orbitworkbench.notification.domain.NotificationEvent;
import com.orbitworkbench.studyplan.domain.StudyTaskRecord;
import com.orbitworkbench.studyplan.infrastructure.mapper.StudyTaskMapper;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StudyDueScannerTest {

    @Mock
    private StudyTaskMapper studyTaskMapper;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private StudyDueScanner scanner;

    @Test
    void scanNotifiesEachDueTaskWithDueDateScopedIdempotencyKey() {
        when(studyTaskMapper.listDueActive(any(LocalDate.class)))
                .thenReturn(List.of(task(11L, 1L, "复习 Redis", LocalDate.parse("2026-08-29")),
                        task(12L, 2L, "整理 CAP", LocalDate.parse("2026-08-30"))));

        int processed = scanner.scan(LocalDate.parse("2026-08-30"));

        assertEquals(2, processed);
        verify(notificationService, times(1)).notify(
                eq(NotificationEvent.STUDY_TASK_DUE), eq(1L), anyString(), anyString(),
                eq(NotificationService.RESOURCE_STUDY_TASK), eq(11L), eq("/study-plan"),
                eq("STUDY_TASK_DUE:11:2026-08-29"));
        verify(notificationService, times(1)).notify(
                eq(NotificationEvent.STUDY_TASK_DUE), eq(2L), anyString(), anyString(),
                eq(NotificationService.RESOURCE_STUDY_TASK), eq(12L), eq("/study-plan"),
                eq("STUDY_TASK_DUE:12:2026-08-30"));
    }

    @Test
    void scanWithNoDueTasksNotifiesNothing() {
        when(studyTaskMapper.listDueActive(any(LocalDate.class))).thenReturn(List.of());
        assertEquals(0, scanner.scan(LocalDate.parse("2026-08-30")));
        verify(notificationService, times(0)).notify(any(), any(), any(), any(), any(), any(), any(), any());
    }

    private static StudyTaskRecord task(Long id, Long userId, String title, LocalDate dueDate) {
        StudyTaskRecord record = new StudyTaskRecord();
        record.setId(id);
        record.setUserId(userId);
        record.setTitle(title);
        record.setDueDate(dueDate);
        return record;
    }
}
