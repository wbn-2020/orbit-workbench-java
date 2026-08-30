package com.orbitworkbench.jobapplication.api;

import com.orbitworkbench.jobapplication.domain.ApplicationStage;
import com.orbitworkbench.jobapplication.domain.JobApplicationRecord;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class JobApplicationDtos {
    private JobApplicationDtos() {}

    public record CreateRequest(
            @NotBlank @Size(max = 128) String company,
            @NotBlank @Size(max = 128) String role,
            @Size(max = 4000) String jdSummary,
            @Size(max = 64) String source,
            LocalDate applyDate,
            LocalDate interviewDate,
            @NotNull @Pattern(regexp = "WATCHING|APPLIED|WRITTEN_TEST|INTERVIEWING|HR|OFFER|CLOSED")
            String stage,
            @Size(max = 128) String salaryNote,
            @Size(max = 128) String contact,
            @Size(max = 512) String note
    ) {}

    public record UpdateRequest(
            @NotBlank @Size(max = 128) String company,
            @NotBlank @Size(max = 128) String role,
            @Size(max = 4000) String jdSummary,
            @Size(max = 64) String source,
            LocalDate applyDate,
            LocalDate interviewDate,
            @Pattern(regexp = "PENDING|PASSED|REJECTED|WITHDRAWN") String result,
            @Size(max = 128) String salaryNote,
            @Size(max = 128) String contact,
            @Size(max = 512) String note
    ) {}

    public record StageRequest(
            @NotNull @Pattern(regexp = "WATCHING|APPLIED|WRITTEN_TEST|INTERVIEWING|HR|OFFER|CLOSED")
            String stage,
            @Size(max = 255) String detail
    ) {}

    public record ApplicationResponse(
            Long id,
            String company,
            String role,
            String jdSummary,
            String source,
            LocalDate applyDate,
            LocalDate interviewDate,
            String stage,
            String result,
            String salaryNote,
            String contact,
            String note,
            boolean archived,
            Instant stageChangedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static ApplicationResponse from(JobApplicationRecord record) {
            return new ApplicationResponse(
                    record.getId(),
                    record.getCompany(),
                    record.getRole(),
                    record.getJdSummary(),
                    record.getSource(),
                    record.getApplyDate(),
                    record.getInterviewDate(),
                    record.getStage() == null ? null : record.getStage().name(),
                    record.getResult() == null ? null : record.getResult().name(),
                    record.getSalaryNote(),
                    record.getContact(),
                    record.getNote(),
                    Boolean.TRUE.equals(record.getArchived()),
                    record.getStageChangedAt(),
                    record.getCreatedAt(),
                    record.getUpdatedAt());
        }
    }

    public record ApplicationListResponse(List<ApplicationResponse> items, StageCountResponse stages) {}

    public record StageCountResponse(
            long watching, long applied, long writtenTest,
            long interviewing, long hr, long offer, long closed
    ) {}
}
