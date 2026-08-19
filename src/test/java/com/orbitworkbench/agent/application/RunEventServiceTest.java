package com.orbitworkbench.agent.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.agent.domain.RunEventRecord;
import com.orbitworkbench.agent.infrastructure.mapper.RunEventMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class RunEventServiceTest {

    @Mock
    private RunEventMapper mapper;

    private RunEventService service;

    @BeforeEach
    void setUp() {
        service = new RunEventService(mapper, new ObjectMapper());
    }

    @Test
    void appendAllocatesSequenceFromAgentRunBeforeInsert() {
        when(mapper.incrementSequence(7L)).thenReturn(1);
        when(mapper.currentSequence(7L)).thenReturn(12L);

        RunEventRecord result = service.append(
                7L, 8L, "output.text.delta", null, Map.of("text", "hello"));

        ArgumentCaptor<RunEventRecord> eventCaptor =
                ArgumentCaptor.forClass(RunEventRecord.class);
        InOrder order = inOrder(mapper);
        order.verify(mapper).incrementSequence(7L);
        order.verify(mapper).currentSequence(7L);
        order.verify(mapper).insert(eventCaptor.capture());

        assertEquals(12L, result.getSequence());
        assertEquals(12L, eventCaptor.getValue().getSequence());
        assertEquals(7L, eventCaptor.getValue().getAgentRunId());
        assertEquals(8L, eventCaptor.getValue().getModelCallId());
        assertEquals("{\"text\":\"hello\"}", eventCaptor.getValue().getPayloadJson());
    }

    @Test
    void appendRejectsMissingRunBeforeReadingOrInsertingEvent() {
        when(mapper.incrementSequence(7L)).thenReturn(0);

        ApiException exception = assertThrows(ApiException.class, () ->
                service.append(7L, null, "run.started", "queued", Map.of()));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
        verify(mapper, never()).currentSequence(7L);
        verify(mapper, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void latestSequenceReadsAgentRunCounter() {
        when(mapper.currentSequence(7L)).thenReturn(19L);

        assertEquals(19L, service.latestSequence(7L));
        verify(mapper).currentSequence(7L);
    }
}
