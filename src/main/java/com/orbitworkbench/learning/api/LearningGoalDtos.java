package com.orbitworkbench.learning.api;

import com.orbitworkbench.learning.domain.LearningGoalRow;
import com.orbitworkbench.learning.domain.LearningGoalStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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

    /** V58：从目标拆一步执行任务（标题去重幂等，同名重复提交不堆任务）。 */
    public record AddGoalTaskRequest(
            @NotBlank @Size(max = 255) String title
    ) {}

    public record LearningGoalResponse(
            String id,
            String title,
            String reason,
            LearningGoalStatus status,
            /** V58：有拆解任务时为派生进度（完成步数/总步数），无任务时保留手动 PUT 的存储值。 */
            int progress,
            String linkedSkill,
            /** V52：由哪条画像事实转化而来；普通目标为 null（non_null 序列化下键缺席）。 */
            Long sourceFactId,
            /** V58：从本目标拆出的任务总数（0 = 还没拆步骤）。 */
            int taskCount,
            /** V58：其中已完成的任务数（SKIPPED 不算完成——跳过的步骤不算走过）。 */
            int completedTaskCount,
            /** V58：progress 是否由任务派生。true 时前端隐藏手动进度、如实显示「完成 M/N 步」。 */
            boolean progressDerived
    ) {
        public static LearningGoalResponse from(LearningGoalRow row) {
            return from(row, null);
        }

        public static LearningGoalResponse from(LearningGoalRow row,
                                                com.orbitworkbench.studyplan.domain.GoalTaskStats stats) {
            int total = stats == null ? 0 : (int) stats.getTotal();
            int done = stats == null ? 0 : (int) stats.getCompleted();
            boolean derived = total > 0;
            int progress = derived ? (int) Math.round(done * 100.0 / total) : row.getProgress();
            return new LearningGoalResponse(
                    String.valueOf(row.getId()),
                    row.getTitle(),
                    row.getReason(),
                    row.getStatus(),
                    progress,
                    row.getLinkedSkill(),
                    row.getSourceFactId(),
                    total,
                    done,
                    derived);
        }
    }
}
