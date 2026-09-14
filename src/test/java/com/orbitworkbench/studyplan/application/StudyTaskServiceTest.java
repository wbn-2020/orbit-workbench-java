package com.orbitworkbench.studyplan.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.studyplan.api.StudyTaskDtos.CreateTaskRequest;
import com.orbitworkbench.studyplan.api.StudyTaskDtos.TaskResponse;
import com.orbitworkbench.studyplan.domain.StudyTaskRecord;
import com.orbitworkbench.studyplan.domain.StudyTaskSource;
import com.orbitworkbench.studyplan.domain.StudyTaskStatus;
import com.orbitworkbench.studyplan.infrastructure.mapper.StudyTaskMapper;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StudyTaskServiceTest {

    @Mock
    private StudyTaskMapper mapper;

    @Mock
    private com.orbitworkbench.interview.application.InterviewReportService reportService;

    @Mock
    private com.orbitworkbench.craft.application.CraftNoteService craftNoteService;

    private StudyTaskService service;

    @BeforeEach
    void setUp() {
        service = new StudyTaskService(mapper, reportService, craftNoteService);
    }

    @Test
    void createMakesManualPlannedTask() {
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.<StudyTaskRecord>getArgument(0).setId(31L);
            return null;
        }).when(mapper).insert(any(StudyTaskRecord.class));

        TaskResponse response = service.create(7L, request());

        ArgumentCaptor<StudyTaskRecord> captor = ArgumentCaptor.forClass(StudyTaskRecord.class);
        verify(mapper).insert(captor.capture());
        assertEquals(31L, response.id());
        assertEquals(StudyTaskSource.MANUAL, captor.getValue().getSourceType());
        assertEquals(StudyTaskStatus.PLANNED, captor.getValue().getStatus());
        assertEquals(Boolean.TRUE, captor.getValue().getManual());
        assertEquals(LocalDate.of(2026, 9, 1), captor.getValue().getDueDate());
    }

    @Test
    void startRequiresPlannedStatus() {
        StudyTaskRecord record = task(StudyTaskStatus.COMPLETED);
        when(mapper.findById(31L)).thenReturn(record);

        assertThrows(ApiException.class, () -> service.start(7L, 31L));
    }

    @Test
    void startTransitionsPlannedToInProgress() {
        StudyTaskRecord record = task(StudyTaskStatus.PLANNED);
        when(mapper.findById(31L)).thenReturn(record);
        when(mapper.updateStatus(eq(31L), eq(7L), eq(StudyTaskStatus.PLANNED),
                eq(StudyTaskStatus.IN_PROGRESS), isNull(), any())).thenReturn(1);

        service.start(7L, 31L);

        verify(mapper).updateStatus(eq(31L), eq(7L), eq(StudyTaskStatus.PLANNED),
                eq(StudyTaskStatus.IN_PROGRESS), isNull(), any());
    }

    @Test
    void completeAllowsPlannedAndInProgressOnly() {
        StudyTaskRecord skipped = task(StudyTaskStatus.SKIPPED);
        when(mapper.findById(31L)).thenReturn(skipped);

        assertThrows(ApiException.class, () -> service.complete(7L, 31L));
    }

    @Test
    void completeTransitionsPlannedToCompleted() {
        StudyTaskRecord record = task(StudyTaskStatus.PLANNED);
        when(mapper.findById(31L)).thenReturn(record);
        when(mapper.updateStatus(eq(31L), eq(7L), eq(StudyTaskStatus.PLANNED),
                eq(StudyTaskStatus.COMPLETED), isNull(), any())).thenReturn(1);

        service.complete(7L, 31L);

        verify(mapper).updateStatus(eq(31L), eq(7L), eq(StudyTaskStatus.PLANNED),
                eq(StudyTaskStatus.COMPLETED), isNull(), any());
    }

    @Test
    void postponeSetsStatusAndDueDate() {
        StudyTaskRecord record = task(StudyTaskStatus.PLANNED);
        when(mapper.findById(31L)).thenReturn(record);
        when(mapper.updateStatus(eq(31L), eq(7L), eq(StudyTaskStatus.PLANNED),
                eq(StudyTaskStatus.POSTPONED), eq(LocalDate.of(2026, 9, 5)), any())).thenReturn(1);

        service.postpone(7L, 31L, LocalDate.of(2026, 9, 5));

        verify(mapper).updateStatus(eq(31L), eq(7L), eq(StudyTaskStatus.PLANNED),
                eq(StudyTaskStatus.POSTPONED), eq(LocalDate.of(2026, 9, 5)), any());
    }

    @Test
    void anotherUsersTaskIsNotFound() {
        StudyTaskRecord record = task(StudyTaskStatus.PLANNED);
        record.setUserId(8L);
        when(mapper.findById(31L)).thenReturn(record);

        assertThrows(ApiException.class, () -> service.complete(7L, 31L));
    }

    @Test
    void generateFromReportCreatesPlannedReportSourcedTasks() {
        when(reportService.readyStudySuggestions(7L, 21L))
                .thenReturn(List.of("补充一次降级压测数据", "整理布隆过滤器参数推导笔记"));
        when(reportService.reportIdOfSession(21L)).thenReturn(33L);
        when(mapper.countBySourceTitle(eq(7L), eq(StudyTaskSource.REPORT), eq(33L), any()))
                .thenReturn(0);

        int created = service.generateFromReport(7L, 21L);

        assertEquals(2, created);
        ArgumentCaptor<StudyTaskRecord> captor = ArgumentCaptor.forClass(StudyTaskRecord.class);
        verify(mapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertEquals(StudyTaskSource.REPORT, captor.getValue().getSourceType());
        assertEquals(33L, captor.getValue().getSourceId());
        assertEquals(Boolean.FALSE, captor.getValue().getManual());
        assertEquals(StudyTaskStatus.PLANNED, captor.getValue().getStatus());
    }

    @Test
    void generateFromReportSkipsDuplicatedTitles() {
        when(reportService.readyStudySuggestions(7L, 21L))
                .thenReturn(List.of("补充一次降级压测数据", "整理布隆过滤器参数推导笔记"));
        when(reportService.reportIdOfSession(21L)).thenReturn(33L);
        when(mapper.countBySourceTitle(eq(7L), eq(StudyTaskSource.REPORT), eq(33L), any()))
                .thenReturn(1, 0);

        int created = service.generateFromReport(7L, 21L);

        assertEquals(1, created);
        verify(mapper, org.mockito.Mockito.times(1)).insert(any(StudyTaskRecord.class));
    }

    @Test
    void generateFromReportRejectsWhenReportHasNoSuggestions() {
        when(reportService.readyStudySuggestions(7L, 21L)).thenReturn(List.of());

        assertThrows(ApiException.class, () -> service.generateFromReport(7L, 21L));
        verify(mapper, never()).insert(any(StudyTaskRecord.class));
    }

    private StudyTaskRecord task(StudyTaskStatus status) {
        StudyTaskRecord record = new StudyTaskRecord();
        record.setId(31L);
        record.setUserId(7L);
        record.setSourceType(StudyTaskSource.MANUAL);
        record.setTitle("复盘 Redis 双写一致性");
        record.setPriority(com.orbitworkbench.studyplan.domain.StudyTaskPriority.HIGH);
        record.setStatus(status);
        record.setManual(true);
        return record;
    }

    @Test
    void postponedTasksCanResumeCompleteSkipOrBePostponedAgain() {
        when(mapper.findById(31L)).thenReturn(task(StudyTaskStatus.POSTPONED));
        when(mapper.updateStatus(eq(31L), eq(7L), eq(StudyTaskStatus.POSTPONED),
                any(), org.mockito.ArgumentMatchers.nullable(LocalDate.class), any())).thenReturn(1);
        service.start(7L, 31L);
        service.complete(7L, 31L);
        service.skip(7L, 31L);
        service.postpone(7L, 31L, LocalDate.of(2026, 9, 20));
        verify(mapper, org.mockito.Mockito.times(4)).updateStatus(eq(31L), eq(7L),
                eq(StudyTaskStatus.POSTPONED), any(),
                org.mockito.ArgumentMatchers.nullable(LocalDate.class), any());
    }

    @Test
    void reportSuggestionRetainsAll300Characters() throws Exception {
        String title = "x".repeat(300);
        when(reportService.readyStudySuggestions(7L, 21L)).thenReturn(List.of(title));
        when(reportService.reportIdOfSession(21L)).thenReturn(33L);
        assertEquals(1, service.generateFromReport(7L, 21L));
        ArgumentCaptor<StudyTaskRecord> captor = ArgumentCaptor.forClass(StudyTaskRecord.class);
        verify(mapper).insert(captor.capture());
        assertEquals(title, captor.getValue().getTitle());
        String migration = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/resources/db/migration/V39__study_task_report_title_length.sql"));
        org.junit.jupiter.api.Assertions.assertTrue(migration.contains("title VARCHAR(300) NOT NULL"));
    }

    @Test
    void createRejectsUnknownPriorityAsBadRequest() {
        ApiException exception = assertThrows(ApiException.class, () -> service.create(7L,
                new CreateTaskRequest("复盘 Redis 双写一致性", "Redis", "REVIEW", "URGENT", 30,
                        LocalDate.of(2026, 9, 1))));

        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("priority 取值不合法", exception.getMessage());
        verify(mapper, never()).insert(any(StudyTaskRecord.class));
    }

    private CreateTaskRequest request() {
        return new CreateTaskRequest(
                " 复盘 Redis 双写一致性 ",
                "Redis",
                "REVIEW",
                "HIGH",
                30,
                LocalDate.of(2026, 9, 1));
    }

    // ---- V49：方法论 → 练习任务 ----

    private static com.orbitworkbench.craft.domain.CraftNoteRecord confirmedCraft(long id) {
        com.orbitworkbench.craft.domain.CraftNoteRecord craft =
                new com.orbitworkbench.craft.domain.CraftNoteRecord();
        craft.setId(id);
        craft.setUserId(7L);
        craft.setCategory("PLAYBOOK");
        craft.setTitle("并发问题五步排查法");
        craft.setWhenToUse("排查并发异常时");
        craft.setContent("1. …");
        craft.setSource(com.orbitworkbench.craft.domain.CraftSource.USER_ENTERED);
        craft.setConfirmationStatus(com.orbitworkbench.craft.domain.CraftStatus.CONFIRMED);
        return craft;
    }

    @Test
    void generateFromCraftCreatesPracticeTaskOnce() {
        when(craftNoteService.requireConfirmed(7L, 5L)).thenReturn(confirmedCraft(5L));
        when(mapper.countBySourceTitle(eq(7L), eq(StudyTaskSource.CRAFT), eq(5L), anyString()))
                .thenReturn(0);

        int created = service.generateFromCraft(7L, 5L);

        assertEquals(1, created);
        ArgumentCaptor<StudyTaskRecord> saved = ArgumentCaptor.forClass(StudyTaskRecord.class);
        verify(mapper).insert(saved.capture());
        assertEquals(StudyTaskSource.CRAFT, saved.getValue().getSourceType());
        assertEquals(5L, saved.getValue().getSourceId());
        assertEquals("练习：并发问题五步排查法", saved.getValue().getTitle());
        assertEquals(StudyTaskStatus.PLANNED, saved.getValue().getStatus());
    }

    @Test
    void generateFromCraftIsIdempotentByTitle() {
        when(craftNoteService.requireConfirmed(7L, 5L)).thenReturn(confirmedCraft(5L));
        when(mapper.countBySourceTitle(eq(7L), eq(StudyTaskSource.CRAFT), eq(5L), anyString()))
                .thenReturn(1);

        assertEquals(0, service.generateFromCraft(7L, 5L));
        verify(mapper, never()).insert(any(StudyTaskRecord.class));
    }

    @Test
    void generateFromCraftRejectsUnconfirmed() {
        // 只有 CONFIRMED 能转练习：候选池不是正式内容，requireConfirmed 会抛
        when(craftNoteService.requireConfirmed(7L, 6L)).thenThrow(
                new ApiException(org.springframework.http.HttpStatus.CONFLICT,
                        com.orbitworkbench.shared.api.ErrorCode.STATE_CONFLICT, "只有已确认的套路可以转成练习"));

        assertThrows(ApiException.class, () -> service.generateFromCraft(7L, 6L));
        verify(mapper, never()).insert(any(StudyTaskRecord.class));
    }

    // ---- V50：练习完成回写 ----

    @Test
    void completeCraftTaskWritesBackPracticeCount() {
        StudyTaskRecord record = task(StudyTaskStatus.PLANNED);
        record.setSourceType(StudyTaskSource.CRAFT);
        record.setSourceId(5L);
        when(mapper.findById(31L)).thenReturn(record);
        when(mapper.updateStatus(eq(31L), eq(7L), eq(StudyTaskStatus.PLANNED),
                eq(StudyTaskStatus.COMPLETED), isNull(), any())).thenReturn(1);

        service.complete(7L, 31L);

        verify(craftNoteService).markPracticed(7L, 5L);
    }

    @Test
    void completeManualTaskDoesNotTouchCrafts() {
        StudyTaskRecord record = task(StudyTaskStatus.PLANNED);
        when(mapper.findById(31L)).thenReturn(record);
        when(mapper.updateStatus(eq(31L), eq(7L), eq(StudyTaskStatus.PLANNED),
                eq(StudyTaskStatus.COMPLETED), isNull(), any())).thenReturn(1);

        service.complete(7L, 31L);

        verify(craftNoteService, never()).markPracticed(any(), any());
    }

    @Test
    void rejectedCompleteDoesNotWriteBack() {
        StudyTaskRecord skipped = task(StudyTaskStatus.SKIPPED);
        skipped.setSourceType(StudyTaskSource.CRAFT);
        skipped.setSourceId(5L);
        when(mapper.findById(31L)).thenReturn(skipped);

        assertThrows(ApiException.class, () -> service.complete(7L, 31L));
        verify(craftNoteService, never()).markPracticed(any(), any());
    }
}
