package com.orbitworkbench.interview.infrastructure.mapper;

import com.orbitworkbench.interview.domain.InterviewSessionRecord;
import com.orbitworkbench.interview.domain.InterviewSessionStatus;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface InterviewSessionMapper {

    void insert(InterviewSessionRecord record);

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
