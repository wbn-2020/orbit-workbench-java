package com.orbitworkbench.learning.api;

import com.orbitworkbench.learning.domain.LearningGoalRow;
import com.orbitworkbench.learning.domain.LearningGoalStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.format.DateTimeFormatter;

/** 学习目标的请求与响应结构（v2 学习更新域）。 */
public final class LearningGoalDtos {

    private LearningGoalDtos() {
    }

    public record CreateLearningGoalRequest(
            @Size(max = 255) String title,
            @Size(max = 1024) String reason,
            @Size(max = 128) String linkedSkill
    ) {}

    public record UpdateLearningGoalRequest(
            @NotNull String status,
            @Min(0) @Max(100) int progress
    ) {}

    public record LearningGoalResponse(
            String id,
            String title,
            String reason,
            LearningGoalStatus status,
            int progress,
            String linkedSkill,
            /** V52：由哪条画像事实转化而来；普通目标为 null（non_null 序列化下键缺席）。 */
            Long sourceFactId
    ) {
        public static LearningGoalResponse from(LearningGoalRow row) {
            return new LearningGoalResponse(
                    String.valueOf(row.getId()),
                    row.getTitle(),
                    row.getReason(),
                    row.getStatus(),
                    row.getProgress(),
                    row.getLinkedSkill(),
                    row.getSourceFactId());
        }
    }
}
