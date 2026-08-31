package com.orbitworkbench.interview.application;

import com.orbitworkbench.interview.api.InterviewDtos;
import com.orbitworkbench.interview.api.InterviewDtos.AddTurnRequest;
import com.orbitworkbench.interview.api.InterviewDtos.CreateSessionRequest;
import com.orbitworkbench.interview.api.InterviewDtos.SessionDetailResponse;
import com.orbitworkbench.interview.api.InterviewDtos.SessionResponse;
import com.orbitworkbench.interview.api.InterviewDtos.SubmitAnswerRequest;
import com.orbitworkbench.interview.api.InterviewDtos.TurnResponse;
import com.orbitworkbench.interview.domain.AnswerSource;
import com.orbitworkbench.interview.domain.InterviewReportRecord;
import com.orbitworkbench.interview.domain.InterviewSessionRecord;
import com.orbitworkbench.interview.domain.InterviewSessionStatus;
import com.orbitworkbench.interview.domain.InterviewTurnRecord;
import com.orbitworkbench.interview.domain.InterviewTurnType;
import com.orbitworkbench.interview.domain.ReportStatus;
import com.orbitworkbench.aiconnection.application.AiConnectionService;
import com.orbitworkbench.aiconnection.application.AiConnectionService.AiConnectionRuntimeConfig;
import com.orbitworkbench.interview.infrastructure.mapper.InterviewReportMapper;
import com.orbitworkbench.interview.infrastructure.mapper.InterviewSessionMapper;
import com.orbitworkbench.interview.infrastructure.mapper.InterviewTurnMapper;
import com.orbitworkbench.interviewer.application.InterviewerService;
import com.orbitworkbench.interviewer.domain.InterviewerProfileRecord;
import com.orbitworkbench.knowledge.domain.FactStatus;
import com.orbitworkbench.knowledge.domain.ProjectFactRecord;
import com.orbitworkbench.knowledge.infrastructure.mapper.ProjectFactMapper;
import com.orbitworkbench.project.domain.ProjectRecord;
import com.orbitworkbench.project.domain.ProjectVersionRecord;
import com.orbitworkbench.project.infrastructure.mapper.ProjectMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.shared.api.RequestEnums;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 面试会话状态机与轮次持久化。
 *
 * <p>当前阶段只实现状态机、配置快照与问答记录持久化；主问题与追问内容由调用方提供，
 * 评分报告在会话结束时进入 REPORT_PENDING，等待 AI 报告生成能力（后续里程碑）。
 * 状态机按 PRD §10.2：READY -> RUNNING <-> PAUSED -> (USER_ENDED ->) COMPLETING，
 * PAUSED 可 CANCELLED，COMPLETING 之后由报告流程推进。</p>
 */
@Service
public class InterviewSessionService {

    private static final int MAX_FACTS_PER_BINDING = 50;

    private final InterviewSessionMapper sessionMapper;
    private final InterviewTurnMapper turnMapper;
    private final InterviewReportMapper reportMapper;
    private final AiConnectionService aiConnectionService;
    private final ProjectMapper projectMapper;
    private final ProjectFactMapper projectFactMapper;
    private final InterviewerService interviewerService;
    private final ObjectMapper objectMapper;

    public InterviewSessionService(InterviewSessionMapper sessionMapper,
                                   InterviewTurnMapper turnMapper,
                                   InterviewReportMapper reportMapper,
                                   AiConnectionService aiConnectionService,
                                   ProjectMapper projectMapper,
                                   ProjectFactMapper projectFactMapper,
                                   InterviewerService interviewerService,
                                   ObjectMapper objectMapper) {
        this.sessionMapper = sessionMapper;
        this.turnMapper = turnMapper;
        this.reportMapper = reportMapper;
        this.aiConnectionService = aiConnectionService;
        this.projectMapper = projectMapper;
        this.projectFactMapper = projectFactMapper;
        this.interviewerService = interviewerService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SessionResponse create(Long userId, CreateSessionRequest request) {
        Instant now = Instant.now();
        InterviewSessionRecord record = new InterviewSessionRecord();
        record.setUserId(userId);
        record.setTitle(request.title().trim());
        record.setTopicMode(request.topicMode());
        record.setForm(request.form());
        record.setRound(request.round());
        record.setProjectBindingsJson(buildProjectBindingsSnapshot(userId, request.projectBindings()));
        record.setWebSearchPolicy(request.webSearchPolicy());
        if (request.aiConnectionId() != null) {
            AiConnectionRuntimeConfig connection =
                    aiConnectionService.getRuntimeConfig(request.aiConnectionId());
            record.setAiConnectionIdSnapshot(connection.connectionId());
            record.setAiModelSnapshot(connection.modelName());
        }
        if (request.interviewerId() != null) {
            InterviewerProfileRecord profile =
                    interviewerService.requireUsable(userId, request.interviewerId());
            record.setInterviewerId(profile.getId());
            record.setInterviewerNameSnapshot(profile.getName());
            record.setInterviewerSnapshotJson(buildInterviewerSnapshot(profile));
        } else {
            record.setInterviewerNameSnapshot(normalize(request.interviewerName()));
        }
        record.setTargetRole(normalize(request.targetRole()));
        record.setTargetExperienceBand(normalize(request.targetExperienceBand()));
        record.setQuestionLimit(request.questionLimit());
        record.setFollowUpLimit(request.followUpLimit());
        record.setTurnLimit(request.turnLimit());
        record.setDurationLimitMinutes(request.durationLimitMinutes());
        record.setScheduledAt(request.scheduledAt());
        record.setStatus(InterviewSessionStatus.READY);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        sessionMapper.insert(record);
        return SessionResponse.from(record);
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> list(Long userId, String status) {
        InterviewSessionStatus statusFilter = parseStatusOrNull(status);
        return sessionMapper.listByUser(userId, statusFilter).stream()
                .map(SessionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public SessionDetailResponse get(Long userId, Long sessionId) {
        InterviewSessionRecord session = ownedSession(userId, sessionId);
        List<TurnResponse> turns = turnMapper.listBySession(session.getId()).stream()
                .map(TurnResponse::from)
                .toList();
        return new SessionDetailResponse(SessionResponse.from(session), turns);
    }

    @Transactional
    public SessionResponse start(Long userId, Long sessionId) {
        InterviewSessionRecord session = ownedSession(userId, sessionId);
        transition(session, InterviewSessionStatus.READY, InterviewSessionStatus.RUNNING,
                Instant.now(), null);
        return SessionResponse.from(reload(session.getId()));
    }

    @Transactional
    public SessionResponse pause(Long userId, Long sessionId) {
        InterviewSessionRecord session = ownedSession(userId, sessionId);
        transition(session, InterviewSessionStatus.RUNNING, InterviewSessionStatus.PAUSED,
                null, null);
        return SessionResponse.from(reload(session.getId()));
    }

    @Transactional
    public SessionResponse resume(Long userId, Long sessionId) {
        InterviewSessionRecord session = ownedSession(userId, sessionId);
        transition(session, InterviewSessionStatus.PAUSED, InterviewSessionStatus.RUNNING,
                null, null);
        return SessionResponse.from(reload(session.getId()));
    }

    @Transactional
    public SessionResponse cancel(Long userId, Long sessionId) {
        InterviewSessionRecord session = ownedSession(userId, sessionId);
        transition(session, InterviewSessionStatus.PAUSED, InterviewSessionStatus.CANCELLED,
                null, Instant.now());
        return SessionResponse.from(reload(session.getId()));
    }

    @Transactional
    public SessionResponse end(Long userId, Long sessionId) {
        InterviewSessionRecord session = ownedSession(userId, sessionId);
        if (session.getStatus() != InterviewSessionStatus.RUNNING
                && session.getStatus() != InterviewSessionStatus.PAUSED) {
            throw conflict("当前状态不允许结束会话：" + session.getStatus());
        }
        Instant now = Instant.now();
        int updated = sessionMapper.updateStatus(session.getId(), session.getStatus(),
                InterviewSessionStatus.COMPLETING, null, now, now);
        if (updated != 1) {
            throw conflict("会话状态已变化，请刷新后重试");
        }

        InterviewReportRecord report = new InterviewReportRecord();
        report.setSessionId(session.getId());
        report.setStatus(ReportStatus.REPORT_PENDING);
        report.setRetryCount(0);
        report.setCreatedAt(now);
        report.setUpdatedAt(now);
        reportMapper.insert(report);
        return SessionResponse.from(reload(session.getId()));
    }

    @Transactional
    public TurnResponse addTurn(Long userId, Long sessionId, AddTurnRequest request) {
        InterviewSessionRecord session = ownedSession(userId, sessionId);
        requireRunning(session);
        requireTurnBudget(session);

        InterviewTurnType turnType = RequestEnums.parse(InterviewTurnType.class,
                request.turnType(), "turnType");
        if (turnType == InterviewTurnType.MAIN) {
            int mainCount = turnMapper.countBySessionAndType(session.getId(), InterviewTurnType.MAIN);
            if (mainCount >= session.getQuestionLimit()) {
                throw conflict("主问题数量已达上限（" + session.getQuestionLimit() + "）");
            }
        }

        Instant now = Instant.now();
        InterviewTurnRecord turn = new InterviewTurnRecord();
        turn.setSessionId(session.getId());
        turn.setTurnNo(turnMapper.countBySession(session.getId()) + 1);
        turn.setTurnType(turnType);
        turn.setQuestion(request.question().trim());
        turn.setCreatedAt(now);
        turnMapper.insert(turn);
        return TurnResponse.from(turn);
    }

    @Transactional
    public TurnResponse submitAnswer(Long userId, Long sessionId, Long turnId, SubmitAnswerRequest request) {
        InterviewSessionRecord session = ownedSession(userId, sessionId);
        requireRunning(session);

        InterviewTurnRecord turn = turnMapper.findById(turnId);
        if (turn == null || !turn.getSessionId().equals(session.getId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "问答不存在");
        }
        if (turn.getAnswer() != null) {
            throw conflict("该问答已提交过回答");
        }
        int updated = turnMapper.submitAnswer(turn.getId(), request.answer(),
                RequestEnums.parse(AnswerSource.class, request.answerSource(), "answerSource")
                        .name(),
                Instant.now());
        if (updated != 1) {
            throw conflict("回答状态已变化，请刷新后重试");
        }
        return TurnResponse.from(turnMapper.findById(turn.getId()));
    }

    private String buildInterviewerSnapshot(InterviewerProfileRecord profile) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("profileId", profile.getId());
        snapshot.put("code", profile.getCode());
        snapshot.put("name", profile.getName());
        snapshot.put("description", profile.getDescription());
        snapshot.put("systemPrompt", profile.getSystemPrompt());
        snapshot.put("topicMode", profile.getTopicMode());
        snapshot.put("focusTags", interviewerService.parseTags(profile.getFocusTagsJson()));
        snapshot.put("defaultQuestionLimit", profile.getDefaultQuestionLimit());
        snapshot.put("defaultFollowUpLimit", profile.getDefaultFollowUpLimit());
        snapshot.put("builtIn", Boolean.TRUE.equals(profile.getBuiltIn()));
        snapshot.put("profileVersion", profile.getVersion());
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("面试官快照序列化失败", exception);
        }
    }

    private String buildProjectBindingsSnapshot(Long userId,
                                                List<InterviewDtos.ProjectBindingRequest> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            return "[]";
        }
        Set<String> seen = new HashSet<>();
        for (InterviewDtos.ProjectBindingRequest binding : bindings) {
            if (!seen.add(binding.projectId() + ":" + binding.versionId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST,
                        "项目版本绑定重复");
            }
        }
        List<Map<String, Object>> snapshotBindings = new ArrayList<>();
        for (InterviewDtos.ProjectBindingRequest binding : bindings) {
            ProjectRecord project =
                    projectMapper.findProjectByIdAndUserId(binding.projectId(), userId);
            if (project == null) {
                throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                        "绑定的项目不存在");
            }
            ProjectVersionRecord version = projectMapper.findVersionById(binding.versionId());
            if (version == null || !version.getProjectId().equals(binding.projectId())) {
                throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                        "绑定的项目版本不存在");
            }
            List<Map<String, Object>> factSnapshots = new ArrayList<>();
            for (ProjectFactRecord fact : projectFactMapper.listByVersion(userId, version.getId())) {
                if (fact.getConfirmationStatus() != FactStatus.CONFIRMED) {
                    continue;
                }
                if (factSnapshots.size() >= MAX_FACTS_PER_BINDING) {
                    break;
                }
                Map<String, Object> factSnapshot = new LinkedHashMap<>();
                factSnapshot.put("factId", fact.getId());
                factSnapshot.put("factType", fact.getFactType());
                factSnapshot.put("title", fact.getTitle());
                factSnapshot.put("content", fact.getContent());
                factSnapshots.add(factSnapshot);
            }
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("projectId", project.getId());
            snapshot.put("projectName", project.getName());
            snapshot.put("versionId", version.getId());
            snapshot.put("versionNumber", version.getVersionNumber());
            snapshot.put("facts", factSnapshots);
            snapshotBindings.add(snapshot);
        }
        try {
            return objectMapper.writeValueAsString(snapshotBindings);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("项目绑定快照序列化失败", exception);
        }
    }

    private InterviewSessionRecord ownedSession(Long userId, Long sessionId) {
        InterviewSessionRecord session = sessionMapper.findById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "面试会话不存在");
        }
        return session;
    }

    private InterviewSessionRecord reload(Long sessionId) {
        return sessionMapper.findById(sessionId);
    }

    private void transition(InterviewSessionRecord session, InterviewSessionStatus expectedCurrent,
                            InterviewSessionStatus target, Instant startedAt, Instant endedAt) {
        if (session.getStatus() != expectedCurrent) {
            throw conflict("状态不允许该操作：当前 " + session.getStatus()
                    + "，该操作要求 " + expectedCurrent + "，目标 " + target);
        }
        Instant now = Instant.now();
        int updated = sessionMapper.updateStatus(session.getId(), session.getStatus(), target,
                startedAt, endedAt, now);
        if (updated != 1) {
            throw conflict("会话状态已变化，请刷新后重试");
        }
    }

    private void requireRunning(InterviewSessionRecord session) {
        if (session.getStatus() != InterviewSessionStatus.RUNNING) {
            throw conflict("会话未在进行中，无法记录问答：当前 " + session.getStatus());
        }
    }

    private void requireTurnBudget(InterviewSessionRecord session) {
        int count = turnMapper.countBySession(session.getId());
        if (count >= session.getTurnLimit()) {
            throw conflict("总问答数已达上限（" + session.getTurnLimit() + "）");
        }
    }

    private InterviewSessionStatus parseStatusOrNull(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return InterviewSessionStatus.valueOf(status);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST,
                    "未知会话状态：" + status);
        }
    }

    private ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT, message);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
