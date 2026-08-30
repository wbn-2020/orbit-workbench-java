package com.orbitworkbench.notification.api;

import com.orbitworkbench.notification.domain.NotificationRecord;
import java.time.Instant;

public final class NotificationDtos {

    private NotificationDtos() {
    }

    public record NotificationResponse(
            Long id,
            String eventType,
            String title,
            String content,
            String resourceType,
            Long resourceId,
            String resourceRoute,
            boolean read,
            Instant readAt,
            Instant createdAt) {

        public static NotificationResponse from(NotificationRecord record) {
            return new NotificationResponse(
                    record.getId(),
                    record.getEventType() == null ? null : record.getEventType().name(),
                    record.getTitle(),
                    record.getContent(),
                    record.getResourceType(),
                    record.getResourceId(),
                    record.getResourceRoute(),
                    record.getReadAt() != null,
                    record.getReadAt(),
                    record.getCreatedAt());
        }
    }

    public record UnreadCountResponse(int count) {
    }

    public record MarkReadResponse(int updated) {
    }
}
