package com.orbitworkbench.worklog.api;

import com.orbitworkbench.worklog.domain.WorkLogCategory;
import com.orbitworkbench.worklog.domain.WorkLogRow;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.format.DateTimeFormatter;

/** 工作记录的请求与响应结构（v2 工作沉淀域）。id 一律以字符串返回，对齐前端契约。 */
public final class WorkLogDtos {

    private WorkLogDtos() {
    }

    public record CreateWorkLogRequest(
            @NotBlank @Size(max = 255) String title,
            @NotBlank @Size(max = 20000) String content,
            @NotBlank @Size(max = 32) String category
    ) {}

    public record WorkLogResponse(
            String id,
            String title,
            String content,
            WorkLogCategory category,
            String createdAt,
            boolean distilled
    ) {
        public static WorkLogResponse from(WorkLogRow row) {
            return new WorkLogResponse(
                    String.valueOf(row.getId()),
                    row.getTitle(),
                    row.getContent(),
                    row.getCategory(),
                    DateTimeFormatter.ISO_INSTANT.format(row.getCreatedAt()),
                    row.isDistilled());
        }
    }
}
