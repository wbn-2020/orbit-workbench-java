package com.orbitworkbench.ai.application;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import reactor.core.publisher.Flux;

public final class AiAdapterCall {

    private final AtomicReference<Integer> httpStatus = new AtomicReference<>();
    private final AtomicBoolean doneMarkerReceived = new AtomicBoolean();
    private Flux<AiStreamEvent> events = Flux.empty();

    public Flux<AiStreamEvent> events() {
        return events;
    }

    public Integer httpStatus() {
        return httpStatus.get();
    }

    public boolean doneMarkerReceived() {
        return doneMarkerReceived.get();
    }

    public void setEvents(Flux<AiStreamEvent> events) {
        this.events = events;
    }

    public void setHttpStatus(Integer status) {
        httpStatus.set(status);
    }

    public void markDoneMarkerReceived() {
        doneMarkerReceived.set(true);
    }
}
