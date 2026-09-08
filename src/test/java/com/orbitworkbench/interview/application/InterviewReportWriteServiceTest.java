package com.orbitworkbench.interview.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import com.orbitworkbench.interview.domain.InterviewSessionRecord;
import com.orbitworkbench.interview.domain.InterviewSessionStatus;
import com.orbitworkbench.interview.infrastructure.mapper.InterviewReportMapper;
import com.orbitworkbench.interview.infrastructure.mapper.InterviewSessionMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InterviewReportWriteServiceTest {

    @Mock
    private InterviewReportMapper reportMapper;

    @Mock
    private InterviewSessionMapper sessionMapper;

    private InterviewReportWriteService service;

    @BeforeEach
    void setUp() {
        service = new InterviewReportWriteService(reportMapper, sessionMapper);
    }

    @Test
    void persistsReportBeforeCompletingSession() {
        when(reportMapper.markReady(eq(21L), eq(84), any(), eq("PASS"),
                eq(InterviewReportService.SCORING_RULE_VERSION), any(), any(), any(), any(), any(), any(),
                any(), any())).thenReturn(1);
        when(sessionMapper.updateStatus(eq(21L), eq(InterviewSessionStatus.COMPLETING),
                eq(InterviewSessionStatus.COMPLETED), isNull(), isNull(), any())).thenReturn(1);

        service.persistReadyAndComplete(session(), payload());

        InOrder order = inOrder(reportMapper, sessionMapper);
        order.verify(reportMapper).markReady(eq(21L), eq(84), any(), eq("PASS"),
                eq(InterviewReportService.SCORING_RULE_VERSION), any(), any(), any(), any(), any(), any(),
                any(), any());
        order.verify(sessionMapper).updateStatus(eq(21L), eq(InterviewSessionStatus.COMPLETING),
                eq(InterviewSessionStatus.COMPLETED), isNull(), isNull(), any());
    }

    @Test
    void rejectsWhenSessionCannotAdvanceSoTransactionRollsBackReportUpdate() {
        when(reportMapper.markReady(eq(21L), eq(84), any(), eq("PASS"),
                eq(InterviewReportService.SCORING_RULE_VERSION), any(), any(), any(), any(), any(), any(),
                any(), any())).thenReturn(1);
        when(sessionMapper.updateStatus(eq(21L), eq(InterviewSessionStatus.COMPLETING),
                eq(InterviewSessionStatus.COMPLETED), isNull(), isNull(), any())).thenReturn(0);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.persistReadyAndComplete(session(), payload()));

        assertEquals(ErrorCode.STATE_CONFLICT, exception.getErrorCode());
    }

    private InterviewSessionRecord session() {
        InterviewSessionRecord session = new InterviewSessionRecord();
        session.setId(21L);
        session.setStatus(InterviewSessionStatus.COMPLETING);
        return session;
    }

    private InterviewReportWriteService.ReadyPayload payload() {
        return new InterviewReportWriteService.ReadyPayload(
                84, "{\"技术正确性\":88}", "PASS", "[]", "[]", "[]", "[]", "[]", "[]");
    }
}
