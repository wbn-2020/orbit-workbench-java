package com.orbitworkbench.interview.infrastructure.mapper;

import com.orbitworkbench.interview.domain.InterviewReportRecord;
import java.time.Instant;
import org.apache.ibatis.annotations.Param;

public interface InterviewReportMapper {

    void insert(InterviewReportRecord record);

    InterviewReportRecord findBySessionId(@Param("sessionId") Long sessionId);

    int markFailed(
            @Param("sessionId") Long sessionId,
            @Param("failureReason") String failureReason,
            @Param("updatedAt") Instant updatedAt);

    int markPending(
            @Param("sessionId") Long sessionId,
            @Param("updatedAt") Instant updatedAt);

    int markReady(
            @Param("sessionId") Long sessionId,
            @Param("totalScore") Integer totalScore,
            @Param("dimensionScoresJson") String dimensionScoresJson,
            @Param("hiringRecommendation") String hiringRecommendation,
            @Param("strengthsJson") String strengthsJson,
            @Param("weaknessesJson") String weaknessesJson,
            @Param("followUpFindingsJson") String followUpFindingsJson,
            @Param("projectMasteryJson") String projectMasteryJson,
            @Param("knowledgeGapsJson") String knowledgeGapsJson,
            @Param("studySuggestionsJson") String studySuggestionsJson,
            @Param("generatedAt") Instant generatedAt,
            @Param("updatedAt") Instant updatedAt);
}
