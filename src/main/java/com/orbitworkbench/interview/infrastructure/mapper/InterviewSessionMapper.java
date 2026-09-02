package com.orbitworkbench.interview.infrastructure.mapper;

import com.orbitworkbench.interview.domain.InterviewSessionRecord;
import com.orbitworkbench.interview.domain.InterviewSessionStatus;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface InterviewSessionMapper {

    void insert(InterviewSessionRecord record);

    /**
     * 出题时把实际联网结论写回会话：这是历史事实，不能等读的时候再按连接当前声明推算
     * （ADR-0012）。只更新这三列，不碰 updated_at，免得干扰既有乐观锁语义。
     */
    int updateWebSearchOutcome(
            @Param("id") Long id,
            @Param("dialect") String dialect,
            @Param("applied") String applied,
            @Param("note") String note);

    InterviewSessionRecord findById(@Param("id") Long id);

    List<InterviewSessionRecord> listByUser(
            @Param("userId") Long userId,
            @Param("status") InterviewSessionStatus status);

    List<InterviewSessionRecord> listScheduledBetween(
            @Param("userId") Long userId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    int countByInterviewer(@Param("interviewerId") Long interviewerId);

    int updateStatus(
            @Param("id") Long id,
            @Param("expectedStatus") InterviewSessionStatus expectedStatus,
            @Param("status") InterviewSessionStatus status,
            @Param("startedAt") Instant startedAt,
            @Param("endedAt") Instant endedAt,
            @Param("updatedAt") Instant updatedAt);
}
