package com.orbitworkbench.schedule.api;

import com.orbitworkbench.schedule.domain.ScheduleEventRecord;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class ScheduleDtos {

    private ScheduleDtos() {
    }

    public record CreateScheduleRequest(
            @NotBlank @Size(max = 255) String title,
            @NotNull Instant startAt,
            Instant endAt,
            boolean allDay,
            @Min(0) @Max(10080) Integer reminderMinutes,
            @Size(max = 255) String resourceRoute,
            @Size(max = 512) String note) {
    }

    public record UpdateScheduleRequest(
            @NotBlank @Size(max = 255) String title,
            @NotNull Instant startAt,
            Instant endAt,
            boolean allDay,
            @Min(0) @Max(10080) Integer reminderMinutes,
            @Size(max = 255) String resourceRoute,
            @Size(max = 512) String note) {
    }

    public record ScheduleEventResponse(
            Long id,
            String sourceType,
            String title,
            Instant startAt,
            Instant endAt,
            boolean allDay,
            String status,
            Integer reminderMinutes,
            String resourceRoute,
            String note,
            Instant createdAt,
            Instant updatedAt) {

        public static ScheduleEventResponse from(ScheduleEventRecord record) {
            return new ScheduleEventResponse(
                    record.getId(),
                    record.getSourceType() == null ? null : record.getSourceType().name(),
                    record.getTitle(),
                    record.getStartAt(),
                    record.getEndAt(),
                    Boolean.TRUE.equals(record.getAllDay()),
                    record.getStatus() == null ? null : record.getStatus().name(),
                    record.getReminderMinutes(),
                    record.getResourceRoute(),
                    record.getNote(),
                    record.getCreatedAt(),
                    record.getUpdatedAt());
        }
    }

    /** 统一日程项：自定义日程（来自 schedule_event 表）与面试/复习/投递实时派生项共用此形状。 */
    public record AgendaItemResponse(
            String sourceType,
            Long sourceId,
            String title,
            Instant startAt,
            Instant endAt,
            boolean allDay,
            String status,
            String resourceRoute) {
    }
}
