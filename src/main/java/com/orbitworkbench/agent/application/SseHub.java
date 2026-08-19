package com.orbitworkbench.agent.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.agent.api.AgentRunDtos.RunEventResponse;
import com.orbitworkbench.agent.domain.RunEventRecord;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class SseHub {

    private static final int REPLAY_BATCH_SIZE = 500;
    private static final int STREAM_LOCK_COUNT = 64;

    private final RunEventService runEventService;
    private final ObjectMapper objectMapper;
    private final Map<Long, CopyOnWriteArrayList<Subscription>> subscriptions =
            new ConcurrentHashMap<>();
    private final Object[] streamLocks = new Object[STREAM_LOCK_COUNT];

    public SseHub(RunEventService runEventService, ObjectMapper objectMapper) {
        this.runEventService = runEventService;
        this.objectMapper = objectMapper;
        for (int index = 0; index < streamLocks.length; index++) {
            streamLocks[index] = new Object();
        }
    }

    public SseEmitter subscribe(Long runId, long afterSequence) {
        SseEmitter emitter = new SseEmitter(0L);
        Subscription subscription = new Subscription(emitter, Math.max(afterSequence, 0L));
        Runnable cleanup = () -> remove(runId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ignored -> cleanup.run());

        Object streamLock = lockFor(runId);
        synchronized (streamLock) {
            CopyOnWriteArrayList<Subscription> runSubscriptions =
                    subscriptions.computeIfAbsent(
                            runId, ignored -> new CopyOnWriteArrayList<>());
            runSubscriptions.add(subscription);
            try {
                replayPersisted(runId, subscription);
            } catch (IOException | IllegalStateException exception) {
                remove(runId, emitter);
                emitter.completeWithError(exception);
            }
        }
        return emitter;
    }

    public void publishAfterCommit(RunEventRecord event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            publish(event);
                        }
                    });
            return;
        }
        publish(event);
    }

    public void publish(RunEventRecord event) {
        Object streamLock = lockFor(event.getAgentRunId());
        synchronized (streamLock) {
            CopyOnWriteArrayList<Subscription> runSubscriptions =
                    subscriptions.get(event.getAgentRunId());
            if (runSubscriptions == null) {
                return;
            }
            List<Subscription> failed = new ArrayList<>();
            for (Subscription subscription : runSubscriptions) {
                try {
                    sendInSequence(subscription, event);
                } catch (IOException | IllegalStateException exception) {
                    failed.add(subscription);
                }
            }
            failed.forEach(subscription -> {
                remove(event.getAgentRunId(), subscription.emitter);
                subscription.emitter.completeWithError(
                        new IllegalStateException("SSE client disconnected"));
            });
        }
    }

    public void closeAll() {
        List<SseEmitter> emitters = new ArrayList<>();
        for (Long runId : new ArrayList<>(subscriptions.keySet())) {
            synchronized (lockFor(runId)) {
                CopyOnWriteArrayList<Subscription> removed = subscriptions.remove(runId);
                if (removed != null) {
                    removed.forEach(subscription -> emitters.add(subscription.emitter));
                }
            }
        }
        emitters.forEach(SseEmitter::complete);
    }

    public SseEmitter.SseEventBuilder toSseEvent(RunEventRecord event) {
        return SseEmitter.event()
                .id(Long.toString(event.getSequence()))
                .name(event.getEventType())
                .data(toPayload(event));
    }

    private Map<String, Object> toPayload(RunEventRecord event) {
        JsonNode data;
        try {
            data = objectMapper.readTree(event.getPayloadJson() == null ? "{}" : event.getPayloadJson());
        } catch (JsonProcessingException exception) {
            data = objectMapper.createObjectNode();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("runId", event.getAgentRunId());
        payload.put("modelCallId", event.getModelCallId());
        payload.put("sequence", event.getSequence());
        payload.put("type", event.getEventType());
        payload.put("summary", event.getEventSummary());
        payload.put("data", data);
        if (data != null && data.has("text") && !data.get("text").isNull()) {
            payload.put("text", data.get("text").asText());
        }
        payload.put("occurredAt", event.getOccurredAt());
        return payload;
    }

    private void remove(Long runId, SseEmitter emitter) {
        Object streamLock = lockFor(runId);
        synchronized (streamLock) {
            CopyOnWriteArrayList<Subscription> runSubscriptions = subscriptions.get(runId);
            if (runSubscriptions != null) {
                runSubscriptions.removeIf(subscription -> subscription.emitter == emitter);
                if (runSubscriptions.isEmpty()) {
                    subscriptions.remove(runId, runSubscriptions);
                }
            }
        }
    }

    private void sendInSequence(Subscription subscription,
                                RunEventRecord event) throws IOException {
        if (event.getSequence() <= subscription.lastSequence) {
            return;
        }
        if (event.getSequence() > subscription.lastSequence + 1) {
            replayPersisted(event.getAgentRunId(), subscription);
        }
        if (event.getSequence() != subscription.lastSequence + 1) {
            return;
        }
        subscription.emitter.send(toSseEvent(event));
        subscription.lastSequence = event.getSequence();
    }

    private void replayPersisted(Long runId,
                                 Subscription subscription) throws IOException {
        while (true) {
            List<RunEventRecord> batch = runEventService.findAfter(
                    runId, subscription.lastSequence, REPLAY_BATCH_SIZE);
            if (batch.isEmpty()) {
                return;
            }
            long sequenceBeforeBatch = subscription.lastSequence;
            for (RunEventRecord persisted : batch) {
                if (persisted.getSequence() <= subscription.lastSequence) {
                    continue;
                }
                if (persisted.getSequence() != subscription.lastSequence + 1) {
                    return;
                }
                subscription.emitter.send(toSseEvent(persisted));
                subscription.lastSequence = persisted.getSequence();
            }
            if (subscription.lastSequence == sequenceBeforeBatch
                    || batch.size() < REPLAY_BATCH_SIZE) {
                return;
            }
        }
    }

    private Object lockFor(Long runId) {
        int index = (Long.hashCode(runId) & Integer.MAX_VALUE) % streamLocks.length;
        return streamLocks[index];
    }

    private static final class Subscription {
        private final SseEmitter emitter;
        private long lastSequence;

        private Subscription(SseEmitter emitter, long lastSequence) {
            this.emitter = emitter;
            this.lastSequence = lastSequence;
        }
    }
}
