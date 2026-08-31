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

    @Test
    void createRejectsUnknownStageAsBadRequest() {
        ApiException exception = assertThrows(ApiException.class, () -> service.create(7L,
                new CreateRequest("美团", "后端开发", null, null, null, null,
                        "BOGUS_STAGE", null, null, null)));

        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("stage 取值不合法", exception.getMessage());
        verify(mapper, org.mockito.Mockito.never()).insert(any(JobApplicationRecord.class));
    }

    @Test
    void advanceRejectsUnknownStageAsBadRequest() {
        when(mapper.findById(41L)).thenReturn(record(ApplicationStage.APPLIED));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.advance(7L, 41L, new StageRequest("HRED", null)));

        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, exception.getStatus());
        verify(mapper, org.mockito.Mockito.never())
                .updateStage(any(), any(), any(), any(), any(), any());
    }

    @Test
    void advanceConflictWritesNoStageTrail() {
        when(mapper.findById(41L)).thenReturn(record(ApplicationStage.APPLIED));
        when(mapper.updateStage(eq(41L), eq(7L), eq(ApplicationStage.APPLIED),
                eq(ApplicationStage.INTERVIEWING), any(), any())).thenReturn(0);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.advance(7L, 41L, new StageRequest("INTERVIEWING", "一面")));

        assertEquals("投递记录已变化，请刷新后重试", exception.getMessage());
        verify(eventMapper, org.mockito.Mockito.never()).insert(any(ApplicationEventRecord.class));
    }

    @Test
    void archivingWritesTrailWithReadableDetail() {
        JobApplicationRecord record = record(ApplicationStage.OFFER);
        when(mapper.findById(41L)).thenReturn(record);
        when(mapper.updateArchived(eq(41L), eq(7L), eq(true), any())).thenReturn(1);

        service.setArchived(7L, 41L, true);

        ArgumentCaptor<ApplicationEventRecord> captor =
                ArgumentCaptor.forClass(ApplicationEventRecord.class);
        verify(eventMapper).insert(captor.capture());
        assertEquals("ARCHIVED", captor.getValue().getEventType());
        assertEquals("归档", captor.getValue().getDetail());
    }

    @Test
    void unarchivingRecordsReadableDetail() {
        JobApplicationRecord record = record(ApplicationStage.OFFER);
        record.setArchived(true);
        when(mapper.findById(41L)).thenReturn(record);
        when(mapper.updateArchived(eq(41L), eq(7L), eq(false), any())).thenReturn(1);

        service.setArchived(7L, 41L, false);

        ArgumentCaptor<ApplicationEventRecord> captor =
                ArgumentCaptor.forClass(ApplicationEventRecord.class);
        verify(eventMapper).insert(captor.capture());
        assertEquals("取消归档", captor.getValue().getDetail());
    }

    @Test
    void deleteRejectedWhenRowAlreadyGone() {
        when(mapper.findById(41L)).thenReturn(record(ApplicationStage.APPLIED));
        when(mapper.delete(41L, 7L)).thenReturn(0);

        assertThrows(ApiException.class, () -> service.delete(7L, 41L));
    }

    @Test
    void deleteRejectedForForeignRecord() {
        JobApplicationRecord foreign = record(ApplicationStage.APPLIED);
        foreign.setUserId(8L);
        when(mapper.findById(41L)).thenReturn(foreign);

        assertThrows(ApiException.class, () -> service.delete(7L, 41L));
        verify(mapper, org.mockito.Mockito.never()).delete(any(), any());
    }

    @Test
    void listAggregatesStageCounts() {
        when(mapper.listByUser(7L, false)).thenReturn(List.of(
                record(ApplicationStage.APPLIED), record(ApplicationStage.INTERVIEWING),
                record(ApplicationStage.INTERVIEWING), record(ApplicationStage.OFFER)));

        var response = service.list(7L, false);

        assertEquals(4, response.items().size());
        assertEquals(1, response.stages().applied());
        assertEquals(2, response.stages().interviewing());
        assertEquals(1, response.stages().offer());
        assertEquals(0, response.stages().closed());
    }

    private CreateRequest request() {
        return new CreateRequest(
                "美团", "后端开发", "高并发方向", "内推",
                LocalDate.of(2026, 8, 28), null, "APPLIED", "30k-50k", "联系人", "内推刚投");
    }
}
