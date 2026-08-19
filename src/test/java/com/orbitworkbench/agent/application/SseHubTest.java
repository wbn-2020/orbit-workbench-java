package com.orbitworkbench.agent.application;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.agent.domain.RunEventRecord;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@ExtendWith(MockitoExtension.class)
class SseHubTest {

    @Mock
    private RunEventService runEventService;

    @Test
    void replayReadsAllPersistedBatches() {
        List<RunEventRecord> firstBatch = events(1, 500);
        List<RunEventRecord> secondBatch = events(501, 501);
        when(runEventService.findAfter(9L, 0L, 500)).thenReturn(firstBatch);
        when(runEventService.findAfter(9L, 500L, 500)).thenReturn(secondBatch);
        SseHub hub = new SseHub(runEventService, new ObjectMapper());

        SseEmitter emitter = hub.subscribe(9L, 0L);
        emitter.complete();

        verify(runEventService).findAfter(9L, 0L, 500);
        verify(runEventService).findAfter(9L, 500L, 500);
    }

    @Test
    void publishReplaysPersistedGapBeforeAdvancingSubscriber() {
        when(runEventService.findAfter(9L, 0L, 500))
                .thenReturn(List.of(), events(1, 3));
        SseHub hub = new SseHub(runEventService, new ObjectMapper());
        SseEmitter emitter = hub.subscribe(9L, 0L);

        hub.publish(events(3, 3).get(0));
        emitter.complete();

        verify(runEventService, times(2)).findAfter(9L, 0L, 500);
    }

    @Test
    void publishAfterCommitDoesNotPublishBeforeCommit() {
        SseHub hub = spy(new SseHub(runEventService, new ObjectMapper()));
        RunEventRecord event = events(1, 1).get(0);
        TransactionSynchronizationManager.initSynchronization();
        try {
            hub.publishAfterCommit(event);

            verify(hub, never()).publish(event);
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(synchronization -> synchronization.afterCommit());
            verify(hub).publish(event);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void publishAfterCommitDoesNotPublishAfterRollback() {
        SseHub hub = spy(new SseHub(runEventService, new ObjectMapper()));
        RunEventRecord event = events(1, 1).get(0);
        TransactionSynchronizationManager.initSynchronization();
        try {
            hub.publishAfterCommit(event);
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(synchronization -> synchronization.afterCompletion(
                            TransactionSynchronization.STATUS_ROLLED_BACK));

            verify(hub, never()).publish(event);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private List<RunEventRecord> events(long from, long to) {
        List<RunEventRecord> events = new ArrayList<>();
        for (long sequence = from; sequence <= to; sequence++) {
            RunEventRecord event = new RunEventRecord();
            event.setId(sequence);
            event.setAgentRunId(9L);
            event.setSequence(sequence);
            event.setEventType("output.text.delta");
            event.setPayloadJson("{\"text\":\"x\"}");
            event.setOccurredAt(Instant.EPOCH.plusSeconds(sequence));
            events.add(event);
        }
        return events;
    }
}
