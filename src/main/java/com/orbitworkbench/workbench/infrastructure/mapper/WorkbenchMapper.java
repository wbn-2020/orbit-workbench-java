package com.orbitworkbench.workbench.infrastructure.mapper;

import com.orbitworkbench.workbench.domain.WorkbenchLastEvaluation;
import java.time.Instant;
import org.apache.ibatis.annotations.Param;

/** 工作台首页聚合查询：只做跨模块的只读统计，不写任何表。 */
public interface WorkbenchMapper {

    long countWorkLogs(@Param("userId") Long userId);

    long countPendingDistill(@Param("userId") Long userId);

    long countKnowledgeCards(@Param("userId") Long userId);

    long countLearningGoals(@Param("userId") Long userId);

    long countActiveGoals(@Param("userId") Long userId);

    long sumFocusMinutesAll(@Param("userId") Long userId);

    long focusMinutesToday(@Param("userId") Long userId,
                           @Param("start") Instant start,
                           @Param("end") Instant end);

    /** 最近一次面试报告：等级与总分，没有则 null。 */
    WorkbenchLastEvaluation lastInterviewGrade(@Param("userId") Long userId);

    // —— 管线体检（借鉴 EvoFlow 运营洞察：让「链路哪里卡住」可见，只读）——

    /** ai_connection 是全局资产（无 user_id 列），按启用数计。 */
    long countEnabledConnections();

    long countProjects(@Param("userId") Long userId);

    long countReadyReports(@Param("userId") Long userId);

    long countFailedReports(@Param("userId") Long userId);

    /** 最近一次 COMPLETED 面试的结束时间，没有则 null。 */
    Instant lastInterviewEndedAt(@Param("userId") Long userId);

    /** 今日到期的复习卡片数（next_review_date <= date）。 */
    long countDueCards(@Param("userId") Long userId, @Param("date") java.time.LocalDate date);

    /** 待确认的用户画像建议数。 */
    long countPendingUserFacts(@Param("userId") Long userId);
}
