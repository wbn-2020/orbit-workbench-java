package com.orbitworkbench.jobprofile.api;

import com.orbitworkbench.jobprofile.domain.JobProfileRecord;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;

public final class JobProfileDtos {
    private JobProfileDtos() {}

    public record JobProfileRequest(
            @NotBlank @Size(max = 128) String targetRole,
            @NotNull @Pattern(regexp = "GRADUATE|ONE_TO_THREE_YEARS|THREE_TO_FIVE_YEARS|FIVE_PLUS_YEARS|CUSTOM")
            String targetExperienceBand,
            @NotNull @Pattern(regexp = "GRADUATE|CAREER_TRANSITION|JOB_CHANGE") String careerStage,
            @Size(max = 32) String targetLevel,
            @Size(max = 128) String targetCompany,
            @NotNull @Pattern(regexp = "BEGINNER|WORKING_KNOWLEDGE|PRACTICAL|ADVANCED")
            String javaSkillLevel,
            @NotNull @Pattern(regexp = "BEGINNER|WORKING_KNOWLEDGE|PRACTICAL|ADVANCED")
            String aiSkillLevel,
            LocalDate targetInterviewDate
    ) {}

    public record JobProfileResponse(
            Long id,
            String targetRole,
            String targetExperienceBand,
            String careerStage,
            String targetLevel,
            String targetCompany,
            String javaSkillLevel,
            String aiSkillLevel,
            LocalDate targetInterviewDate,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static JobProfileResponse from(JobProfileRecord record) {
            return new JobProfileResponse(
                    record.getId(),
                    record.getTargetRole(),
                    record.getTargetExperienceBand(),
                    record.getCareerStage(),
                    record.getTargetLevel(),
                    record.getTargetCompany(),
                    record.getJavaSkillLevel(),
                    record.getAiSkillLevel(),
                    record.getTargetInterviewDate(),
                    record.getCreatedAt(),
                    record.getUpdatedAt());
        }
    }

    public record JobProfileStateResponse(boolean completed, JobProfileResponse profile) {
        public static JobProfileStateResponse empty() {
            return new JobProfileStateResponse(false, null);
        }

        public static JobProfileStateResponse from(JobProfileRecord record) {
            return new JobProfileStateResponse(true, JobProfileResponse.from(record));
        }
    }
}
