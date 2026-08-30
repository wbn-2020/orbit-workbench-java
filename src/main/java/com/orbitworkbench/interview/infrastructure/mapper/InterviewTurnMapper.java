package com.orbitworkbench.interview.infrastructure.mapper;

import com.orbitworkbench.interview.domain.InterviewTurnRecord;
import com.orbitworkbench.interview.domain.InterviewTurnType;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface InterviewTurnMapper {

    void insert(InterviewTurnRecord record);

    InterviewTurnRecord findById(@Param("id") Long id);

    List<InterviewTurnRecord> listBySession(@Param("sessionId") Long sessionId);

    int countBySession(@Param("sessionId") Long sessionId);

    int countBySessionAndType(
            @Param("sessionId") Long sessionId,
            @Param("turnType") InterviewTurnType turnType);

    int submitAnswer(
            @Param("id") Long id,
            @Param("answer") String answer,
            @Param("answerSource") String answerSource,
            @Param("answeredAt") Instant answeredAt);
}
