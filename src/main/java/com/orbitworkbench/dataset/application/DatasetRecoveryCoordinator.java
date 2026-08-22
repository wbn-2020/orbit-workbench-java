package com.orbitworkbench.dataset.application;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class DatasetRecoveryCoordinator {

    private final DatasetParsePersistenceService persistenceService;

    public DatasetRecoveryCoordinator(
            DatasetParsePersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        persistenceService.recoverInterrupted();
    }
}
