package com.orbitworkbench.jobapplication.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orbitworkbench.jobapplication.api.JobApplicationDtos.CreateRequest;
import com.orbitworkbench.jobapplication.api.JobApplicationDtos.StageRequest;
import com.orbitworkbench.jobapplication.api.JobApplicationDtos.UpdateRequest;
import com.orbitworkbench.jobapplication.domain.ApplicationEventRecord;
import com.orbitworkbench.jobapplication.domain.ApplicationStage;
import com.orbitworkbench.jobapplication.domain.JobApplicationRecord;
import com.orbitworkbench.jobapplication.infrastructure.mapper.ApplicationEventMapper;
import com.orbitworkbench.jobapplication.infrastructure.mapper.JobApplicationMapper;
import com.orbitworkbench.shared.api.ApiException;
import java.time.LocalDate;
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
class JobApplicationServiceTest {

    @Mock
    private JobApplicationMapper mapper;

    @Mock
    private ApplicationEventMapper eventMapper;

    private JobApplicationService service;

    @BeforeEach
    void setUp() {
        service = new JobApplicationService(mapper, eventMapper);
    }

    @Test
    void createWritesApplicationWithStageAndCreatedEvent() {
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.<JobApplicationRecord>getArgument(0).setId(41L);
            return null;
        }).when(mapper).insert(any(JobApplicationRecord.class));

        var response = service.create(7L, request());

        ArgumentCaptor<JobApplicationRecord> captor =
                ArgumentCaptor.forClass(JobApplicationRecord.class);
        verify(mapper).insert(captor.capture());
        assertEquals(41L, response.id());
        assertEquals(ApplicationStage.APPLIED, captor.getValue().getStage());
        assertEquals(Boolean.FALSE, captor.getValue().getArchived());

        ArgumentCaptor<ApplicationEventRecord> eventCaptor =
                ArgumentCaptor.forClass(ApplicationEventRecord.class);
        verify(eventMapper).insert(eventCaptor.capture());
        assertEquals("CREATED", eventCaptor.getValue().getEventType());
        assertEquals(ApplicationStage.APPLIED, eventCaptor.getValue().getToStage());
    }

    @Test
    void advanceMovesStageWithOptimisticGuardAndWritesTrail() {
        JobApplicationRecord record = record(ApplicationStage.APPLIED);
        when(mapper.findById(41L)).thenReturn(record);
        when(mapper.updateStage(eq(41L), eq(7L), eq(ApplicationStage.APPLIED),
                eq(ApplicationStage.INTERVIEWING), any(), any())).thenReturn(1);
        when(mapper.findById(41L)).thenReturn(record);

        service.advance(7L, 41L, new StageRequest("INTERVIEWING", "一面已约"));

        verify(mapper).updateStage(eq(41L), eq(7L), eq(ApplicationStage.APPLIED),
                eq(ApplicationStage.INTERVIEWING), any(), any());
        ArgumentCaptor<ApplicationEventRecord> captor =
                ArgumentCaptor.forClass(ApplicationEventRecord.class);
        verify(eventMapper).insert(captor.capture());
        assertEquals("STAGE_CHANGED", captor.getValue().getEventType());
        assertEquals(ApplicationStage.APPLIED, captor.getValue().getFromStage());
        assertEquals(ApplicationStage.INTERVIEWING, captor.getValue().getToStage());
    }

    @Test
    void advanceRejectsSameStage() {
        JobApplicationRecord record = record(ApplicationStage.APPLIED);
        when(mapper.findById(41L)).thenReturn(record);

        assertThrows(ApiException.class,
                () -> service.advance(7L, 41L, new StageRequest("APPLIED", null)));
        verify(mapper, org.mockito.Mockito.never())
                .updateStage(any(), any(), any(), any(), any(), any());
    }

    @Test
    void anotherUsersRecordIsNotFound() {
        JobApplicationRecord record = record(ApplicationStage.APPLIED);
        record.setUserId(8L);
        when(mapper.findById(41L)).thenReturn(record);

        assertThrows(ApiException.class, () -> service.get(7L, 41L));
    }

    @Test
    void updateDoesNotTouchStage() {
        JobApplicationRecord record = record(ApplicationStage.INTERVIEWING);
        when(mapper.findById(41L)).thenReturn(record);
        when(mapper.updateEditable(record)).thenReturn(1);

        service.update(7L, 41L, new UpdateRequest(
                "美团", "后端开发", "JD", "内推", LocalDate.of(2026, 8, 20),
                null, "PENDING", "30k", "联系人", null));

        verify(mapper).updateEditable(record);
        assertEquals(ApplicationStage.INTERVIEWING, record.getStage());
    }

    private JobApplicationRecord record(ApplicationStage stage) {
        JobApplicationRecord record = new JobApplicationRecord();
        record.setId(41L);
        record.setUserId(7L);
        record.setCompany("美团");
        record.setRole("后端开发");
        record.setStage(stage);
        record.setArchived(false);
        return record;
    }

    private CreateRequest request() {
        return new CreateRequest(
                "美团", "后端开发", "高并发方向", "内推",
                LocalDate.of(2026, 8, 28), null, "APPLIED", "30k-50k", "联系人", "内推刚投");
    }
}
