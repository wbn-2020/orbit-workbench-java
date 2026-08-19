package com.orbitworkbench.workspace.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.workspace.domain.WorkspaceRecord;
import com.orbitworkbench.workspace.infrastructure.mapper.WorkspaceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class WorkspaceServiceTest {

    @Mock
    private WorkspaceMapper mapper;

    private WorkspaceService service;

    @BeforeEach
    void setUp() {
        service = new WorkspaceService(mapper);
    }

    @Test
    void createDefaultPersistsSingletonMarker() {
        doAnswer(invocation -> {
            WorkspaceRecord record = invocation.getArgument(0);
            record.setId(1L);
            return null;
        }).when(mapper).insert(any(WorkspaceRecord.class));

        WorkspaceRecord result = service.createDefault();

        ArgumentCaptor<WorkspaceRecord> captor =
                ArgumentCaptor.forClass(WorkspaceRecord.class);
        verify(mapper).insert(captor.capture());
        assertEquals(1, captor.getValue().getDefaultKey());
        assertEquals(1L, result.getId());
    }

    @Test
    void deleteRejectsDefaultWorkspaceBeforeDependencyChecks() {
        WorkspaceRecord workspace = new WorkspaceRecord();
        workspace.setId(1L);
        workspace.setDefaultKey(1);
        workspace.setStatus("ACTIVE");
        when(mapper.findByIdForUpdate(1L)).thenReturn(workspace);

        ApiException exception = assertThrows(
                ApiException.class, () -> service.delete(1L));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals(ErrorCode.STATE_CONFLICT, exception.getErrorCode());
        verify(mapper, never()).countAll();
        verify(mapper, never()).softDelete(1L);
    }
}
