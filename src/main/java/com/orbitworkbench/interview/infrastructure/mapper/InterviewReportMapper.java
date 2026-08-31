package com.orbitworkbench.interview.infrastructure.mapper;

import com.orbitworkbench.interview.domain.AnswerSourceCount;
import com.orbitworkbench.interview.domain.InterviewReportRecord;
import com.orbitworkbench.interview.domain.ReportCenterRow;
import com.orbitworkbench.interview.domain.RuleVersionSample;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface InterviewReportMapper {

    void insert(InterviewReportRecord record);

    InterviewReportRecord findBySessionId(@Param("sessionId") Long sessionId);

    /**
     * 报告中心列表。归属只能靠 {@code interview_session.user_id} 判定（interview_report 没有 user_id），
     * 所有新查询都必须走这个 JOIN，不允许直接按报告 id 取数。
     */
    List<ReportCenterRow> listByUser(
            @Param("userId") Long userId,
            @Param("days") Integer days,
            @Param("topicMode") String topicMode,
            @Param("form") String form,
            @Param("recommendation") String recommendation,
            @Param("interviewerId") Long interviewerId,
            @Param("limit") int limit,
            @Param("offset") int offset);

    long countByUser(
            @Param("userId") Long userId,
            @Param("days") Integer days,
            @Param("topicMode") String topicMode,
            @Param("form") String form,
            @Param("recommendation") String recommendation,
            @Param("interviewerId") Long interviewerId);

    /** 趋势只统计同一规则版本内已就绪的报告；规则版本为空时不取任何行。 */
    List<ReportCenterRow> summaryCandidates(
            @Param("userId") Long userId,
            @Param("days") Integer days,
            @Param("ruleVersion") String ruleVersion);

    List<RuleVersionSample> readyRuleVersionCounts(
            @Param("userId") Long userId,
            @Param("days") Integer days);

    ReportCenterRow findOwned(
            @Param("userId") Long userId,
            @Param("reportId") Long reportId);

    List<AnswerSourceCount> answerSourceMix(@Param("sessionId") Long sessionId);

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
            @Param("scoringRuleVersion") String scoringRuleVersion,
            @Param("strengthsJson") String strengthsJson,
            @Param("weaknessesJson") String weaknessesJson,
            @Param("followUpFindingsJson") String followUpFindingsJson,
            @Param("projectMasteryJson") String projectMasteryJson,
            @Param("knowledgeGapsJson") String knowledgeGapsJson,
            @Param("studySuggestionsJson") String studySuggestionsJson,
            @Param("generatedAt") Instant generatedAt,
            @Param("updatedAt") Instant updatedAt);
}
