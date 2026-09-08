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
}
