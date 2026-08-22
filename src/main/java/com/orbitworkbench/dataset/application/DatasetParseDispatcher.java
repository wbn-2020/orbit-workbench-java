package com.orbitworkbench.dataset.application;

import com.orbitworkbench.shared.api.ErrorCode;
import java.util.concurrent.RejectedExecutionException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
public class DatasetParseDispatcher {

    private final ThreadPoolTaskExecutor executor;
    private final DatasetParseCoordinator coordinator;
    private final DatasetParsePersistenceService persistenceService;

    public DatasetParseDispatcher(
            @Qualifier("datasetTaskExecutor") ThreadPoolTaskExecutor executor,
            DatasetParseCoordinator coordinator,
            DatasetParsePersistenceService persistenceService) {
        this.executor = executor;
        this.coordinator = coordinator;
        this.persistenceService = persistenceService;
    }

    public void submit(Long datasetId, boolean alreadyMarked) {
        try {
            executor.execute(() -> coordinator.execute(datasetId, alreadyMarked));
        } catch (RejectedExecutionException exception) {
            if (!alreadyMarked) {
                persistenceService.begin(datasetId);
            }
            persistenceService.fail(
                    datasetId,
                    ErrorCode.UPSTREAM_UNAVAILABLE,
                    "数据集解析队列已满，请稍后重试");
        }
    }
}
