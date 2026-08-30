package com.orbitworkbench.schedule.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orbitworkbench.interview.domain.InterviewSessionRecord;
import com.orbitworkbench.interview.infrastructure.mapper.InterviewSessionMapper;
import com.orbitworkbench.jobapplication.domain.JobApplicationRecord;
import com.orbitworkbench.jobapplication.infrastructure.mapper.JobApplicationMapper;
import com.orbitworkbench.schedule.api.ScheduleDtos;
import com.orbitworkbench.schedule.api.ScheduleDtos.AgendaItemResponse;
import com.orbitworkbench.schedule.api.ScheduleDtos.CreateScheduleRequest;
import com.orbitworkbench.schedule.api.ScheduleDtos.ScheduleEventResponse;
import com.orbitworkbench.schedule.domain.ScheduleEventRecord;
import com.orbitworkbench.schedule.domain.ScheduleSourceType;
import com.orbitworkbench.schedule.domain.ScheduleStatus;
import com.orbitworkbench.schedule.infrastructure.mapper.ScheduleEventMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.studyplan.domain.StudyTaskRecord;
import com.orbitworkbench.studyplan.infrastructure.mapper.StudyTaskMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

    @Mock
    private ScheduleEventMapper scheduleMapper;
    @Mock
    private InterviewSessionMapper interviewMapper;
    @Mock
    private StudyTaskMapper studyTaskMapper;
    @Mock
    private JobApplicationMapper applicationMapper;

    @InjectMocks
    private ScheduleService service;

    private static final Instant T = Instant.parse("2026-08-30T00:00:00Z");

    @Test
    void createCustomPersistsPlannedEventWithCustomSource() {
        CreateScheduleRequest request = new CreateScheduleRequest(
                "线下宣讲", T.plusSeconds(86400), T.plusSeconds(86400 + 3600),
                false, 30, "/applications", "记得带简历");

        ScheduleEventResponse response = service.createCustom(1L, request);

        ArgumentCaptor<ScheduleEventRecord> captor = ArgumentCaptor.forClass(ScheduleEventRecord.class);
        verify(scheduleMapper).insert(captor.capture());
        ScheduleEventRecord saved = captor.getValue();
        assertEquals(ScheduleSourceType.CUSTOM, saved.getSourceType());
        assertEquals(ScheduleStatus.PLANNED, saved.getStatus());
        assertEquals(1L, saved.getUserId());
        assertEquals(null, saved.getSourceId());
        assertEquals("线下宣讲", response.title());
    }

    @Test
    void createCustomRejectsEndNotAfterStart() {
        CreateScheduleRequest request = new CreateScheduleRequest(
                "坏日程", T, T.minusSeconds(1), false, null, null, null);

        assertThrows(ApiException.class, () -> service.createCustom(1L, request));
        verify(scheduleMapper, never()).insert(any());
    }

    @Test
    void updateRejectsDerivedEvent() {
        ScheduleEventRecord derived = custom(9L);
        derived.setSourceType(ScheduleSourceType.INTERVIEW);
        when(scheduleMapper.findByIdAndUser(9L, 1L)).thenReturn(derived);

        assertThrows(ApiException.class, () -> service.updateCustom(1L, 9L,
                new ScheduleDtos.UpdateScheduleRequest("x", T, null, false, null, null, null)));
        verify(scheduleMapper, never()).update(any(), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.anyBoolean(), any(), any(), any(), any());
    }

    @Test
    void updateMissingReturnsNotFound() {
        when(scheduleMapper.findByIdAndUser(404L, 1L)).thenReturn(null);
        ApiException ex = assertThrows(ApiException.class, () -> service.setStatus(1L, 404L,
                ScheduleStatus.COMPLETED));
        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void agendaMergesCustomAndDerivedSortedByStart() {
        ScheduleEventRecord custom = custom(100L);
        custom.setStartAt(T.plusSeconds(2L * 86400));
        custom.setEndAt(T.plusSeconds(2L * 86400 + 1800));
        custom.setAllDay(false);
        custom.setResourceRoute("/applications");
        when(scheduleMapper.listCustomBetween(eq(1L), any(), any())).thenReturn(List.of(custom));

        InterviewSessionRecord interview = new InterviewSessionRecord();
        interview.setId(50L);
        interview.setTitle("字节 · 二面");
        interview.setScheduledAt(T.plusSeconds(86400));
        interview.setDurationLimitMinutes(45);
        when(interviewMapper.listScheduledBetween(eq(1L), any(), any())).thenReturn(List.of(interview));

        StudyTaskRecord task = new StudyTaskRecord();
        task.setId(60L);
        task.setTitle("复习 CAP");
        task.setDueDate(LocalDate.parse("2026-09-02"));
        when(studyTaskMapper.listActiveByDueRange(eq(1L), any(), any())).thenReturn(List.of(task));

        JobApplicationRecord application = new JobApplicationRecord();
        application.setId(70L);
        application.setCompany("美团");
        application.setRole("后端");
        application.setInterviewDate(LocalDate.parse("2026-09-03"));
        when(applicationMapper.listWithInterviewDateBetween(eq(1L), any(), any()))
                .thenReturn(List.of(application));

        List<AgendaItemResponse> items = service.agenda(1L, T, T.plusSeconds(30L * 86400));

        assertEquals(4, items.size());
        assertEquals(List.of("INTERVIEW", "CUSTOM", "STUDY_TASK", "APPLICATION"),
                items.stream().map(AgendaItemResponse::sourceType).toList());
        AgendaItemResponse first = items.get(0);
        assertEquals(50L, first.sourceId());
        assertEquals(T.plusSeconds(86400 + 45 * 60), first.endAt());
        assertEquals("/interviews/50", first.resourceRoute());
        assertTrue(items.get(2).allDay());
        assertEquals("/study-plan", items.get(2).resourceRoute());
        assertEquals("美团 · 后端", items.get(3).title());
    }

    private static ScheduleEventRecord custom(Long id) {
        ScheduleEventRecord record = new ScheduleEventRecord();
        record.setId(id);
        record.setUserId(1L);
        record.setSourceType(ScheduleSourceType.CUSTOM);
        record.setTitle("自定义");
        record.setStartAt(T);
        record.setAllDay(true);
        record.setStatus(ScheduleStatus.PLANNED);
        record.setCreatedAt(T);
        record.setUpdatedAt(T);
        return record;
    }
}
