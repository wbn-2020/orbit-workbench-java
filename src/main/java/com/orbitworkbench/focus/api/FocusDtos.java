package com.orbitworkbench.focus.api;

import com.orbitworkbench.focus.domain.FocusMode;
import com.orbitworkbench.focus.domain.FocusSessionRow;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.format.DateTimeFormatter;

/** 专注计时的请求与响应结构（v2 专注计时域）。 */
public final class FocusDtos {

    private FocusDtos() {
    }

    public record SaveFocusSessionRequest(
            @NotBlank String startedAt,
            @Min(1) @Max(1440) int durationMinutes,
            @NotBlank @Size(max = 16) String mode,
            @Size(max = 255) String label
    ) {}

    public record FocusSessionResponse(
            String id,
            String startedAt,
            int durationMinutes,
            FocusMode mode,
            String label
    ) {
        public static FocusSessionResponse from(FocusSessionRow row) {
            return new FocusSessionResponse(
                    String.valueOf(row.getId()),
                    DateTimeFormatter.ISO_INSTANT.format(row.getStartedAt()),
                    row.getDurationMinutes(),
                    row.getMode(),
                    row.getLabel());
        }
    }

    public record FocusStatResponse(
            String date,
            int focusMinutes,
            int sessions
    ) {}
}
