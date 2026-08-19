package com.orbitworkbench.task.application;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.task.api.TaskDtos.CreateTaskRequest;
import com.orbitworkbench.task.api.TaskDtos.TaskResponse;
import com.orbitworkbench.task.domain.TaskCreateIdempotencyRecord;
import com.orbitworkbench.task.domain.TaskRecord;
import com.orbitworkbench.task.infrastructure.mapper.TaskMapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskMapper taskMapper;

    private TaskService taskService;

    @BeforeEach
    void setUp() {
        taskService = new TaskService(taskMapper);
    }

    @Test
    void lockForRunReturnsDatabaseLockedTask() {
        TaskRecord task = task("READY", 42L);
        when(taskMapper.findByIdForUpdate(1L)).thenReturn(task);

        TaskRecord result = taskService.lockForRun(1L);

        assertSame(task, result);
        verify(taskMapper).findByIdForUpdate(1L);
    }

    @Test
    void validateLockedRunInputUsesTaskConnection() {
        TaskRecord task = task("READY", 42L);
        when(taskMapper.lockUsableConnection(42L)).thenReturn(42L);

        taskService.validateLockedRunInput(task);

        verify(taskMapper).lockUsableConnection(42L);
    }

    @Test
    void validateLockedRunInputRejectsUnusableConnection() {
        TaskRecord task = task("READY", 42L);
        when(taskMapper.lockUsableConnection(42L)).thenReturn(null);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> taskService.validateLockedRunInput(task));

        assertApiException(exception);
    }

    @ParameterizedTest
    @ValueSource(strings = {"READY", "FAILED", "CANCELLED", "PAUSED"})
    void attachRunAllowsExplicitSourceStatus(String status) {
        TaskRecord task = task(status, 42L);
        when(taskMapper.updateRunState(1L, 7L, "RUNNING", status)).thenReturn(1);

        TaskRecord result = taskService.attachRun(task, 7L, Set.of(status));

        assertAll(
                () -> assertSame(task, result),
                () -> assertEquals("RUNNING", result.getStatus()),
                () -> assertEquals("QUEUED", result.getCurrentRunStatus()),
                () -> assertEquals(7L, result.getCurrentRunId()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "DRAFT", "FAILED", "CANCELLED", "QUEUED", "RUNNING",
            "PAUSED", "SUCCEEDED", "RECOVERY_REQUIRED"
    })
    void attachRunRejectsStatusOutsideExplicitSet(String status) {
        ApiException exception = assertThrows(
                ApiException.class,
                () -> taskService.attachRun(
                        task(status, 42L), 7L, Set.of("READY")));

        assertApiException(exception);
        verify(taskMapper, never()).updateRunState(
                1L, 7L, "RUNNING", status);
    }

    @Test
    void attachRunRejectsConcurrentStatusChange() {
        TaskRecord task = task("READY", 42L);
        when(taskMapper.updateRunState(1L, 7L, "RUNNING", "READY")).thenReturn(0);

        ApiException exception = assertThrows(ApiException.class,
                () -> taskService.attachRun(task, 7L, Set.of("READY")));

        assertApiException(exception);
    }

    @Test
    void createRejectsDocumentFromAnotherWorkspaceAsStateConflict() {
        CreateTaskRequest request = request("LEARNING_NOTE", List.of(99L));
        when(taskMapper.lockActiveWorkspace(1L)).thenReturn(1L);
        when(taskMapper.lockUsableConnection(42L)).thenReturn(42L);
        when(taskMapper.lockDocuments(1L, List.of(99L))).thenReturn(List.of());

        ApiException exception = assertThrows(
                ApiException.class,
                () -> taskService.create(request));

        assertApiException(exception);
        verify(taskMapper, never()).insert(any(TaskRecord.class));
    }

    @Test
    void createRejectsArtifactTypeOutsideMvpWhitelist() {
        CreateTaskRequest request = request("CUSTOM_REPORT", List.of());

        ApiException exception = assertThrows(
                ApiException.class,
                () -> taskService.create(request));

        assertAll(
                () -> assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus()),
                () -> assertEquals(ErrorCode.VALIDATION_FAILED, exception.getErrorCode()));
        verify(taskMapper, never()).insert(any(TaskRecord.class));
    }

    @Test
    void createLocksActiveParentsBeforeWritingTask() {
        CreateTaskRequest request = request("LEARNING_NOTE", List.of());
        when(taskMapper.lockActiveWorkspace(1L)).thenReturn(1L);
        when(taskMapper.lockUsableConnection(42L)).thenReturn(42L);
        when(taskMapper.findById(7L)).thenAnswer(invocation -> {
            TaskRecord task = task("READY", 42L);
            task.setId(7L);
            task.setWorkspaceId(1L);
            task.setModuleType("TECH_LEARNING");
            task.setTitle("学习任务");
            task.setDescription("任务说明");
            task.setExpectedArtifactType("LEARNING_NOTE");
            task.setPriority("NORMAL");
            return task;
        });
        when(taskMapper.findDocumentIds(7L)).thenReturn(List.of());
        doAnswer(invocation -> {
            TaskRecord task = invocation.getArgument(0);
            task.setId(7L);
            return null;
        }).when(taskMapper).insert(any(TaskRecord.class));

        taskService.create(request);

        InOrder order = inOrder(taskMapper);
        order.verify(taskMapper).lockActiveWorkspace(1L);
        order.verify(taskMapper).lockUsableConnection(42L);
        order.verify(taskMapper).insert(any(TaskRecord.class));
    }

    @Test
    void idempotentCreateReusesExistingTaskAndWritesDocumentsOnce() {
        CreateTaskRequest request = request("LEARNING_NOTE", List.of());
        TaskRecord persisted = task("READY", 42L);
        persisted.setId(7L);
        persisted.setWorkspaceId(1L);
        persisted.setModuleType("TECH_LEARNING");
        persisted.setTitle("学习任务");
        persisted.setDescription("任务说明");
        persisted.setExpectedArtifactType("LEARNING_NOTE");
        persisted.setPriority("NORMAL");
        String[] fingerprint = new String[1];
        when(taskMapper.insertCreateReservation(
                org.mockito.ArgumentMatchers.eq("task-key"),
                any(String.class),
                any(java.time.Instant.class)))
                .thenReturn(1, 0);
        when(taskMapper.lockActiveWorkspace(1L)).thenReturn(1L);
        when(taskMapper.lockUsableConnection(42L)).thenReturn(42L);
        doAnswer(invocation -> {
            TaskRecord inserted = invocation.getArgument(0);
            inserted.setId(7L);
            return null;
        }).when(taskMapper).insert(any(TaskRecord.class));
        doAnswer(invocation -> {
            fingerprint[0] = invocation.getArgument(1);
            return 1;
        }).when(taskMapper).attachCreatedTask(
                org.mockito.ArgumentMatchers.eq("task-key"),
                any(String.class),
                org.mockito.ArgumentMatchers.eq(7L),
                any(java.time.Instant.class));
        when(taskMapper.findCreateReservationForUpdate("task-key"))
                .thenAnswer(invocation -> {
                    TaskCreateIdempotencyRecord reservation =
                            new TaskCreateIdempotencyRecord();
                    reservation.setIdempotencyKey("task-key");
                    reservation.setRequestFingerprint(fingerprint[0]);
                    reservation.setTaskId(7L);
                    return reservation;
                });
        when(taskMapper.findById(7L)).thenReturn(persisted);
        when(taskMapper.findDocumentIds(7L)).thenReturn(List.of());

        TaskResponse first = taskService.create(request, "task-key");
        TaskResponse second = taskService.create(request, "task-key");

        assertAll(
                () -> assertEquals(7L, first.id()),
                () -> assertEquals(7L, second.id()),
                () -> verify(taskMapper, times(1)).insert(any(TaskRecord.class)),
                () -> verify(taskMapper, times(1)).deleteTaskDocuments(7L));
    }

    @Test
    void idempotencyKeyCannotBeReusedForDifferentPayload() {
        CreateTaskRequest request = request("LEARNING_NOTE", List.of());
        TaskCreateIdempotencyRecord reservation = new TaskCreateIdempotencyRecord();
        reservation.setIdempotencyKey("task-key");
        reservation.setRequestFingerprint("different-request");
        reservation.setTaskId(7L);
        when(taskMapper.insertCreateReservation(
                org.mockito.ArgumentMatchers.eq("task-key"),
                any(String.class),
                any(java.time.Instant.class)))
                .thenReturn(0);
        when(taskMapper.findCreateReservationForUpdate("task-key"))
                .thenReturn(reservation);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> taskService.create(request, "task-key"));

        assertAll(
                () -> assertEquals(HttpStatus.CONFLICT, exception.getStatus()),
                () -> assertEquals(ErrorCode.DUPLICATE_REQUEST, exception.getErrorCode()));
        verify(taskMapper, never()).insert(any(TaskRecord.class));
    }

    private TaskRecord task(String status, Long connectionId) {
        TaskRecord task = new TaskRecord();
        task.setId(1L);
        task.setStatus(status);
        task.setConnectionId(connectionId);
        return task;
    }

    private CreateTaskRequest request(String artifactType, List<Long> documentIds) {
        return new CreateTaskRequest(
                1L,
                "TECH_LEARNING",
                "学习任务",
                "任务说明",
                artifactType,
                "NORMAL",
                documentIds,
                42L);
    }

    private void assertApiException(ApiException exception) {
        assertAll(
                () -> assertEquals(HttpStatus.CONFLICT, exception.getStatus()),
                () -> assertEquals(ErrorCode.STATE_CONFLICT, exception.getErrorCode()));
    }
}
