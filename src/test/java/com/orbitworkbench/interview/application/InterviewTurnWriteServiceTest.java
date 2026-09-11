package com.orbitworkbench.interview.application;

import com.orbitworkbench.interview.domain.*;
import com.orbitworkbench.interview.infrastructure.mapper.*;
import com.orbitworkbench.shared.api.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class InterviewTurnWriteServiceTest {
    private final InterviewSessionMapper sessions = mock(InterviewSessionMapper.class);
    private final InterviewTurnMapper turns = mock(InterviewTurnMapper.class);
    private final InterviewTurnWriteService service = new InterviewTurnWriteService(sessions, turns);

    private void session(InterviewSessionStatus status) {
        var session = new InterviewSessionRecord();
        session.setUserId(7L);
        session.setStatus(status);
        session.setQuestionLimit(3);
        session.setFollowUpLimit(3);
        session.setTurnLimit(6);
        when(sessions.findByIdForUpdate(21L)).thenReturn(session);
    }

    @Test
    void rejectsLateQuestionAfterEndOrPause() {
        for (var status : new InterviewSessionStatus[] {
                InterviewSessionStatus.COMPLETING, InterviewSessionStatus.COMPLETED,
                InterviewSessionStatus.PAUSED, InterviewSessionStatus.CANCELLED}) {
            session(status);
            assertEquals(HttpStatus.CONFLICT, assertThrows(ApiException.class,
                    () -> service.append(7L, 21L, InterviewTurnType.MAIN, "question", 0)).getStatus());
        }
        verify(turns, never()).insert(any());
    }

    @Test
    void rejectsConcurrentProgressAndExhaustedBudget() {
        session(InterviewSessionStatus.RUNNING);
        when(turns.countBySession(21L)).thenReturn(1);
        assertThrows(ApiException.class,
                () -> service.append(7L, 21L, InterviewTurnType.MAIN, "question", 0));
        when(turns.countBySessionAndType(21L, InterviewTurnType.MAIN)).thenReturn(3);
        assertThrows(ApiException.class,
                () -> service.append(7L, 21L, InterviewTurnType.MAIN, "question", 1));
        verify(turns, never()).insert(any());
    }

    @Test
    void savesOnlyAfterLockingAndCheckingOwner() {
        session(InterviewSessionStatus.RUNNING);
        assertThrows(ApiException.class,
                () -> service.append(8L, 21L, InterviewTurnType.MAIN, "question", 0));
        var result = service.append(7L, 21L, InterviewTurnType.MAIN, "question", 0);
        assertEquals(1, result.getTurnNo());
        var order = inOrder(sessions, turns);
        order.verify(sessions, atLeastOnce()).findByIdForUpdate(21L);
        order.verify(turns).countBySession(21L);
        order.verify(turns).countBySessionAndType(21L, InterviewTurnType.MAIN);
        order.verify(turns).insert(result);
    }
}
