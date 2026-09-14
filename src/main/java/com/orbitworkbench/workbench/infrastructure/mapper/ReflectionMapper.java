package com.orbitworkbench.workbench.infrastructure.mapper;

import com.orbitworkbench.workbench.domain.FocusMetricRow;
import com.orbitworkbench.workbench.domain.KnowledgeMetricRow;
import com.orbitworkbench.workbench.domain.LearningMetricRow;
import com.orbitworkbench.workbench.domain.ReportMetricRow;
import com.orbitworkbench.workbench.domain.StudyMetricRow;
import com.orbitworkbench.workbench.domain.WorkMetricRow;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 周期复盘的窗口聚合查询：全部只读，按 [start, end) 半开区间过滤创建时间，
 * Java 侧只做本地日期归组。所有表都以 user_id 归属过滤（interview_report / focus
 * 经各自 JOIN 或自身的 user_id 列），不依赖任何跨用户视图。
 */
public interface ReflectionMapper {

    List<WorkMetricRow> listWorkLogs(@Param("userId") Long userId,
                                     @Param("start") Instant start,
                                     @Param("end") Instant end);

    List<KnowledgeMetricRow> listKnowledgeCards(@Param("userId") Long userId,
                                                @Param("start") Instant start,
                                                @Param("end") Instant end);

    List<FocusMetricRow> listFocus(@Param("userId") Long userId,
                                   @Param("start") Instant start,
                                   @Param("end") Instant end);

    List<LearningMetricRow> listLearningGoals(@Param("userId") Long userId,
                                              @Param("start") Instant start,
                                              @Param("end") Instant end);

    List<StudyMetricRow> listStudyTasks(@Param("userId") Long userId,
                                        @Param("start") Instant start,
                                        @Param("end") Instant end);

    List<ReportMetricRow> listReports(@Param("userId") Long userId,
                                      @Param("start") Instant start,
                                      @Param("end") Instant end);

    /** 当前仍在进行（PLANNED/IN_PROGRESS/POSTPONED）的复习任务数，不随窗口变化。 */
    long countActiveStudyTasks(@Param("userId") Long userId);

    /** V56 成长沉淀①：窗口内新确认且仍生效的画像事实（后来被归档的不算沉淀下来）。 */
    long countNewConfirmedFacts(@Param("userId") Long userId,
                                @Param("start") Instant start,
                                @Param("end") Instant end);

    /** V56 成长沉淀②：窗口内练过的套路（按最近练熟时间归属，多次只算一次）。 */
    long countCraftsPracticed(@Param("userId") Long userId,
                              @Param("start") Instant start,
                              @Param("end") Instant end);

    /** V56 成长沉淀③：窗口内完成的练习任务（COMPLETED 状态变更必写 updated_at，同复习任务口径）。 */
    long countPracticeTasksCompleted(@Param("userId") Long userId,
                                     @Param("start") Instant start,
                                     @Param("end") Instant end);
}
