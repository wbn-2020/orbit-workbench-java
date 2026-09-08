package com.orbitworkbench.interview.application;

import com.orbitworkbench.interview.domain.InterviewSessionRecord;
import com.orbitworkbench.interview.domain.InterviewSessionStatus;
import com.orbitworkbench.interview.infrastructure.mapper.InterviewReportMapper;
import com.orbitworkbench.interview.infrastructure.mapper.InterviewSessionMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 报告成功结果的短事务写回。
 *
 * <p>模型调用始终在事务外执行；这里只保证报告和会话状态不会停在半完成状态。</p>
 */
@Service
public class InterviewReportWriteService {

    private final InterviewReportMapper reportMapper;
    private final InterviewSessionMapper sessionMapper;

    public InterviewReportWriteService(InterviewReportMapper reportMapper,
                                       InterviewSessionMapper sessionMapper) {
        this.reportMapper = reportMapper;
        this.sessionMapper = sessionMapper;
    }

    @Transactional
    public void persistReadyAndComplete(InterviewSessionRecord session, ReadyPayload payload) {
        Instant now = Instant.now();
        if (reportMapper.markReady(session.getId(), payload.totalScore(), payload.dimensionScoresJson(),
                payload.hiringRecommendation(), InterviewReportService.SCORING_RULE_VERSION,
                payload.strengthsJson(), payload.weaknessesJson(), payload.followUpFindingsJson(),
                payload.projectMasteryJson(), payload.knowledgeGapsJson(), payload.studySuggestionsJson(),
                now, now) != 1) {
            throw conflict();
        }
        if (sessionMapper.updateStatus(session.getId(), InterviewSessionStatus.COMPLETING,
                InterviewSessionStatus.COMPLETED, null, null, now) != 1) {
            throw conflict();
        }
    }

    private ApiException conflict() {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                "报告或会话状态已变化，请刷新后重试");
    }

    public record ReadyPayload(
            Integer totalScore,
            String dimensionScoresJson,
            String hiringRecommendation,
            String strengthsJson,
            String weaknessesJson,
            String followUpFindingsJson,
            String projectMasteryJson,
            String knowledgeGapsJson,
            String studySuggestionsJson) {}
}
