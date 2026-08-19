package com.orbitworkbench.document.api;

import java.time.Instant;

public final class DocumentDtos {

    private DocumentDtos() {
    }

    public record DocumentSummaryResponse(
            Long id,
            Long workspaceId,
            String fileName,
            String originalName,
            Long sizeBytes,
            String mediaType,
            String parseStatus,
            Instant createdAt
    ) {
    }

    public record DocumentDetailResponse(
            Long id,
            Long workspaceId,
            String fileName,
            String originalName,
            Long sizeBytes,
            String mediaType,
            String parseStatus,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record DocumentText(
            Long id,
            String name,
            String content
    ) {
    }

    public record DocumentContent(
            String mediaType,
            String content
    ) {
    }

    public record StorageCleanupFailureResponse(
            Long id,
            String resourceType,
            Long resourceId,
            String storageRefFingerprint,
            String operation,
            String status,
            String failureType,
            String failureSummary,
            int retryCount,
            Instant lastRetriedAt,
            Instant resolvedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
