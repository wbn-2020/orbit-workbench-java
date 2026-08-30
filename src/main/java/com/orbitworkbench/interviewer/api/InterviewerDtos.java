package com.orbitworkbench.interviewer.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class InterviewerDtos {
    private InterviewerDtos() {}

    public record CreateInterviewerRequest(
            @NotBlank @Size(max = 64) String name,
            @Size(max = 512) String description,
            @NotBlank @Size(max = 4000) String systemPrompt,
            @NotBlank
            @Pattern(regexp = "ROTE|PROJECT_DEEP_DIVE|AI_TECH|CODE_REVIEW|FULL_PROCESS|TRANSITION_TEACHING")
            String topicMode,
            @Size(max = 10) List<@NotBlank @Size(max = 32) String> focusTags,
            @NotNull @Min(1) @Max(50) Integer defaultQuestionLimit,
            @NotNull @Min(0) @Max(20) Integer defaultFollowUpLimit
    ) {}

    public record UpdateInterviewerRequest(
            @NotBlank @Size(max = 64) String name,
            @Size(max = 512) String description,
            @NotBlank @Size(max = 4000) String systemPrompt,
            @NotBlank
            @Pattern(regexp = "ROTE|PROJECT_DEEP_DIVE|AI_TECH|CODE_REVIEW|FULL_PROCESS|TRANSITION_TEACHING")
            String topicMode,
            @Size(max = 10) List<@NotBlank @Size(max = 32) String> focusTags,
            @NotNull @Min(1) @Max(50) Integer defaultQuestionLimit,
            @NotNull @Min(0) @Max(20) Integer defaultFollowUpLimit
    ) {}

    public record CopyInterviewerRequest(
            @Size(max = 64) String name
    ) {}

    public record InterviewerResponse(
            Long id,
            String code,
            String name,
            String description,
            String systemPrompt,
            String topicMode,
            List<String> focusTags,
            Integer defaultQuestionLimit,
            Integer defaultFollowUpLimit,
            boolean builtIn,
            boolean archived,
            Integer version,
            Instant createdAt,
            Instant updatedAt
    ) {}
}
