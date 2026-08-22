package com.orbitworkbench.search.api;

import java.time.Instant;

public final class SearchDtos {

    private SearchDtos() {
    }

    public enum ResourceType {
        TASK,
        DATASET,
        DOCUMENT,
        ARTIFACT,
        RUN
    }

    public record SearchResultResponse(
            ResourceType resourceType,
            Long resourceId,
            String title,
            String summary,
            Instant updatedAt,
            String route
    ) {
    }
}
