package com.orbitworkbench.studyplan.api;

import com.orbitworkbench.studyplan.domain.StudyTaskRecord;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;

public final class StudyTaskDtos {
    private StudyTaskDtos() {}

    public record CreateTaskRequest(
            @NotBlank @Size(max = 255) String title,
            @Size(max = 128) String topic,
            @Size(max = 32) String taskType,
            @NotNull @Pattern(regexp = "HIGH|MEDIUM|LOW") String priority,
            @Min(5) @Max(480) Integer estimatedMinutes,
            LocalDate dueDate
    ) {}

    public record PostponeRequest(LocalDate dueDate) {}

    public record TaskResponse(
            Long id,
            String sourceType,
            Long sourceId,
            String title,
            String topic,
            String taskType,
            String priority,
            Integer estimatedMinutes,
            LocalDate dueDate,
            String status,
            boolean manual,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static TaskResponse from(StudyTaskRecord record) {
            return new TaskResponse(
                    record.getId(),
                    record.getSourceType() == null ? null : record.getSourceType().name(),
                    record.getSourceId(),
                    record.getTitle(),
                    record.getTopic(),
                    record.getTaskType(),
                    record.getPriority() == null ? null : record.getPriority().name(),
                    record.getEstimatedMinutes(),
                    record.getDueDate(),
                    record.getStatus() == null ? null : record.getStatus().name(),
                    Boolean.TRUE.equals(record.getManual()),
                    record.getCreatedAt(),
                    record.getUpdatedAt());
        }
    }
}
