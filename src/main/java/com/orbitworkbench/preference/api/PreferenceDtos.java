package com.orbitworkbench.preference.api;

import com.orbitworkbench.preference.domain.UserPreferenceRecord;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class PreferenceDtos {

    private PreferenceDtos() {
    }

    public record PreferenceRequest(
            @NotNull Boolean notifyReportReady,
            @NotNull Boolean notifyStudyDue,
            @NotNull Boolean notifyInterview,
            @NotNull Boolean notifyImportFailure,
            @NotNull Boolean notifyAiFailure,
            @NotBlank @Size(max = 64) String timezoneId,
            // 保留期可选：null = 永久保留（默认）；非空时须 >= 7 天，避免配成 1 天连刚产生的账目都留不住
            @jakarta.validation.constraints.Min(7) @jakarta.validation.constraints.Max(3650)
            Integer auditRetentionDays,
            @jakarta.validation.constraints.Min(7) @jakarta.validation.constraints.Max(3650)
            Integer notificationRetentionDays,
            @NotNull @Min(1) Integer expectedVersion) {
    }

    public record PreferenceResponse(
            Long userId,
            boolean notifyReportReady,
            boolean notifyStudyDue,
            boolean notifyInterview,
            boolean notifyImportFailure,
            boolean notifyAiFailure,
            String timezoneId,
            Integer auditRetentionDays,
            Integer notificationRetentionDays,
            int version,
            Instant createdAt,
            Instant updatedAt) {

        public static PreferenceResponse from(UserPreferenceRecord record) {
            return new PreferenceResponse(
                    record.getUserId(),
                    record.isNotifyReportReady(),
                    record.isNotifyStudyDue(),
                    record.isNotifyInterview(),
                    record.isNotifyImportFailure(),
                    record.isNotifyAiFailure(),
                    record.getTimezoneId(),
                    record.getAuditRetentionDays(),
                    record.getNotificationRetentionDays(),
                    record.getVersion(),
                    record.getCreatedAt(),
                    record.getUpdatedAt());
        }
    }
}
