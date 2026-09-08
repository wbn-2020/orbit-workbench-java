package com.orbitworkbench.interview.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.ai.application.AiErrorSanitizer;
import com.orbitworkbench.ai.application.AiOutputCleaner;
import com.orbitworkbench.ai.application.RequestRejectedException;
import com.orbitworkbench.ai.application.WebSearchMode;
import com.orbitworkbench.aiconnection.application.AiScenarioExecutionService;
import com.orbitworkbench.aiconnection.domain.AiScenario;
import com.orbitworkbench.interview.api.InterviewDtos.ReportResponse;
import com.orbitworkbench.interview.api.InterviewDtos.ReportStateResponse;
import com.orbitworkbench.interview.domain.InterviewReportRecord;
import com.orbitworkbench.interview.domain.InterviewSessionRecord;
import com.orbitworkbench.interview.domain.InterviewSessionStatus;
import com.orbitworkbench.interview.domain.InterviewTurnRecord;
import com.orbitworkbench.interview.domain.ReportStatus;
import com.orbitworkbench.interview.infrastructure.mapper.InterviewReportMapper;
import com.orbitworkbench.interview.infrastructure.mapper.InterviewSessionMapper;
import com.orbitworkbench.interview.infrastructure.mapper.InterviewTurnMapper;
import com.orbitworkbench.notification.application.NotificationService;
import com.orbitworkbench.notification.domain.NotificationEvent;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 面试评分报告生成。
 *
 * <p>通过 {@code INTERVIEW_REPORT} 场景解析账户并调用模型（含调用审计与备用切换），
 * 对 REPORT_PENDING 的报告生成结构化评分；
 * 输出经 JSON 解析与字段校验后回写 REPORT_READY，并把会话从 COMPLETING 推进到 COMPLETED。
 * 模型调用阻塞且在事务外进行，写回为单条原子 UPDATE；任何失败落入 REPORT_FAILED 并递增
 * retry_count，failure_reason 只保存错误摘要，不包含凭据与模型原文。</p>
 */
@Service
public class InterviewReportService {

    private static final Duration MODEL_TIMEOUT = Duration.ofSeconds(180);
    private static final int MAX_OUTPUT_TOKENS = 4096;
    private static final int TURN_SNIPPET_LIMIT = 4000;
    private static final List<String> RECOMMENDATIONS = List.of("STRONG_PASS", "PASS", "HOLD", "FAIL");
    private static final int MAX_DIMENSIONS = 30;
    private static final int MAX_DIMENSION_NAME = 40;
    private static final int MAX_LIST_ITEMS = 20;
    private static final int MAX_LIST_ITEM_CHARS = 300;

    private static final String SYSTEM_PROMPT = """
            你是一名严格的 Java 后端面试评估官。根据给定的面试配置与完整问答记录输出评分报告。
            评分维度固定为 11 项：业务理解、技术正确性、原理理解、实现深度、项目实践能力、
            问题分析、方案完整性、架构取舍、排障与异常恢复、表达结构、边界意识。每项 0-100。
            录用建议只能是：STRONG_PASS、PASS、HOLD、FAIL 之一。
            评估要点：技术正确性、回答深度、与追问的对抗表现、回答来源（INDEPENDENT 高于 PROMPTED，
            AI_ASSISTED/AI_GENERATED 需在评语中降低独立性评价）。
            只输出一个 JSON 对象，不要输出任何其他文字或代码围栏，结构如下：
            {"totalScore":整数0-100,
             "hiringRecommendation":"STRONG_PASS|PASS|HOLD|FAIL",
             "dimensionScores":{"业务理解":0-100,"技术正确性":0-100,"原理理解":0-100,"实现深度":0-100,
               "项目实践能力":0-100,"问题分析":0-100,"方案完整性":0-100,"架构取舍":0-100,
               "排障与异常恢复":0-100,"表达结构":0-100,"边界意识":0-100},
             "strengths":["做得好的地方"],
             "weaknesses":["薄弱点"],
             "followUpFindings":["追问暴露的问题"],
             "projectMastery":["项目掌握薄弱点"],
             "knowledgeGaps":["技术知识薄弱点"],
             "studySuggestions":["建议的复习任务，每条一句话可执行"]}
            """;

    /**
     * 评分规则版本（14 §4）：哈希材料是「这份报告怎么被打出来」的全部内容——评分提示词、
     * 解析上限与录用建议集合。改任意一项都会得到新版本，不引入需要人工声明的版本表。
     */
    static final String SCORING_RULE_VERSION = computeScoringRuleVersion();

    private static String computeScoringRuleVersion() {
        String material = SYSTEM_PROMPT + '\n' + MAX_DIMENSIONS + '|' + MAX_DIMENSION_NAME
                + '|' + MAX_LIST_ITEMS + '|' + MAX_LIST_ITEM_CHARS + '|'
                + String.join(",", RECOMMENDATIONS);
        final byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境缺少 SHA-256", exception);
        }
        StringBuilder hex = new StringBuilder("rule-");
        for (int index = 0; index < 6; index++) {
            hex.append(String.format("%02x", digest[index]));
        }
        return hex.toString();
    }

    private final InterviewSessionMapper sessionMapper;
    private final InterviewTurnMapper turnMapper;
    private final InterviewReportMapper reportMapper;
    private final AiScenarioExecutionService aiScenarioExecution;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    private final InterviewReportWriteService reportWriteService;

    public InterviewReportService(InterviewSessionMapper sessionMapper,
                                  InterviewTurnMapper turnMapper,
                                  InterviewReportMapper reportMapper,
                                  AiScenarioExecutionService aiScenarioExecution,
                                  ObjectMapper objectMapper,
                                  NotificationService notificationService,
                                  InterviewReportWriteService reportWriteService) {
        this.sessionMapper = sessionMapper;
        this.turnMapper = turnMapper;
        this.reportMapper = reportMapper;
        this.aiScenarioExecution = aiScenarioExecution;
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
        this.reportWriteService = reportWriteService;
    }

    public ReportStateResponse generate(Long userId, Long sessionId, Long connectionId) {
        InterviewSessionRecord session = ownedSession(userId, sessionId);
        requireGeneratableReport(session.getId());

        String userPrompt = buildUserPrompt(session, turnMapper.listBySession(session.getId()));

        ScoredReport scored;
        try {
            // 报告沿用会话上的联网意愿：一场面试的出题与评分必须处在同样的信息条件下，
            // 否则「按最新资料出的题」会配一份「只按站内资料评的分」。生效结论记在调用审计里。
            String modelOutput = aiScenarioExecution.executeText(AiScenario.INTERVIEW_REPORT, userId,
                    connectionId, SYSTEM_PROMPT, userPrompt, MAX_OUTPUT_TOKENS, MODEL_TIMEOUT,
                    WebSearchMode.parse(session.getWebSearchPolicy()));
            scored = parseScoredReport(modelOutput);
        } catch (RequestRejectedException exception) {
            // 请求根本没发出去：报告状态不动、也不发通知，只把这个 4xx 原样回给调用方。
            // 记成「报告生成失败」会谎报后果——用户以为内容丢了，其实一步都没走。
            throw exception;
        } catch (ApiException exception) {
            markFailedAndNotify(session, exception);
            throw exception;
        }
        persistScoredReport(session, scored);
        return new ReportStateResponse(true,
                ReportResponse.from(reportMapper.findBySessionId(session.getId())));
    }

    public ReportStateResponse retry(Long userId, Long sessionId) {
        InterviewSessionRecord session = ownedSession(userId, sessionId);
        InterviewReportRecord report = reportMapper.findBySessionId(session.getId());
        if (report == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "报告不存在");
        }
        if (report.getStatus() != ReportStatus.REPORT_FAILED) {
            throw conflict("报告当前不可重试，请刷新后再试");
        }
        if (reportMapper.markPending(session.getId(), Instant.now()) != 1) {
            throw conflict("报告状态已变化，请刷新后重试");
        }
        return generate(userId, sessionId, null);
    }

    public List<String> readyStudySuggestions(Long userId, Long sessionId) {
        InterviewSessionRecord session = ownedSession(userId, sessionId);
        InterviewReportRecord report = reportMapper.findBySessionId(session.getId());
        if (report == null || report.getStatus() != ReportStatus.REPORT_READY
                || report.getStudySuggestionsJson() == null) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(report.getStudySuggestionsJson());
            List<String> result = new ArrayList<>();
            node.forEach(item -> result.add(item.asText()));
            return result;
        } catch (Exception exception) {
            return List.of();
        }
    }

    @Transactional(readOnly = true)
    public ReportStateResponse getReportState(Long userId, Long sessionId) {
        InterviewSessionRecord session = ownedSession(userId, sessionId);
        InterviewReportRecord report = reportMapper.findBySessionId(session.getId());
        if (report == null) {
            if (session.getStatus() == InterviewSessionStatus.COMPLETING
                    || session.getStatus() == InterviewSessionStatus.COMPLETED) {
                throw conflict("会话已结束但报告记录缺失，请联系排查");
            }
            return ReportStateResponse.empty();
        }
        return new ReportStateResponse(true, ReportResponse.from(report));
    }

    public Long reportIdOfSession(Long sessionId) {
        InterviewReportRecord report = reportMapper.findBySessionId(sessionId);
        if (report == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "报告不存在");
        }
        return report.getId();
    }

    private void persistScoredReport(InterviewSessionRecord session, ScoredReport scored) {
        reportWriteService.persistReadyAndComplete(session, new InterviewReportWriteService.ReadyPayload(
                scored.totalScore(), toJson(scored.dimensionScores()), scored.hiringRecommendation(),
                toJson(scored.strengths()), toJson(scored.weaknesses()), toJson(scored.followUpFindings()),
                toJson(scored.projectMastery()), toJson(scored.knowledgeGaps()),
                toJson(scored.studySuggestions())));
        notificationService.notify(
                NotificationEvent.INTERVIEW_REPORT_READY,
                session.getUserId(),
                "面试报告已生成",
                "本场面试评分报告已就绪（总分 " + scored.totalScore() + "，建议 "
                        + scored.hiringRecommendation() + "），点击查看分项点评与复习建议。",
                NotificationService.RESOURCE_INTERVIEW_SESSION,
                session.getId(),
                "/interviews/" + session.getId() + "/report",
                "INTERVIEW_REPORT_READY:" + session.getId());
    }

    private void markFailedAndNotify(InterviewSessionRecord session, ApiException exception) {
        if (reportMapper.markFailed(session.getId(),
                AiErrorSanitizer.sanitize(exception.getMessage(), null), Instant.now()) != 1) {
            throw conflict("报告状态已变化，请刷新后重试");
        }
        notificationService.notify(
                NotificationEvent.INTERVIEW_REPORT_FAILED,
                session.getUserId(),
                "面试报告生成失败",
                "报告生成未完成，可稍后在报告页重试。",
                NotificationService.RESOURCE_INTERVIEW_SESSION,
                session.getId(),
                "/interviews/" + session.getId(),
                "INTERVIEW_REPORT_FAILED:" + session.getId());
    }

    ScoredReport parseScoredReport(String modelOutput) {
        JsonNode root;
        String json = AiOutputCleaner.extractJsonObject(modelOutput);
        if (json == null) {
            throw unstructured("模型输出中没有 JSON 对象");
        }
        try {
            root = objectMapper.readTree(json);
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unstructured("模型输出不是合法 JSON");
        }
        Integer totalScore = root.path("totalScore").isInt() ? root.path("totalScore").asInt() : null;
        if (totalScore == null || totalScore < 0 || totalScore > 100) {
            throw unstructured("totalScore 缺失或不在 0-100");
        }
        String recommendation = root.path("hiringRecommendation").asText(null);
        if (recommendation == null || !RECOMMENDATIONS.contains(recommendation)) {
            throw unstructured("hiringRecommendation 非法");
        }
        JsonNode dims = root.path("dimensionScores");
        if (!dims.isObject() || dims.isEmpty()) {
            throw unstructured("dimensionScores 缺失");
        }
        Map<String, Integer> dimensionScores = new LinkedHashMap<>();
        int index = 0;
        for (Iterator<Map.Entry<String, JsonNode>> it = dims.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> field = it.next();
            index += 1;
            if (index > MAX_DIMENSIONS) {
                throw unstructured("评分维度数量超过 " + MAX_DIMENSIONS);
            }
            String key = field.getKey().trim();
            if (key.isEmpty() || key.length() > MAX_DIMENSION_NAME) {
                throw unstructured("第 " + index + " 个评分维度名称非法");
            }
            int value = field.getValue().isNumber() ? field.getValue().asInt() : -1;
            if (value < 0 || value > 100) {
                throw unstructured("第 " + index + " 个评分维度取值非法");
            }
            dimensionScores.put(key, value);
        }
        return new ScoredReport(totalScore, recommendation, dimensionScores,
                readStrings(root, "strengths"), readStrings(root, "weaknesses"),
                readStrings(root, "followUpFindings"), readStrings(root, "projectMastery"),
                readStrings(root, "knowledgeGaps"), readStrings(root, "studySuggestions"));
    }

    record ScoredReport(Integer totalScore,
                        String hiringRecommendation,
                        Map<String, Integer> dimensionScores,
                        List<String> strengths,
                        List<String> weaknesses,
                        List<String> followUpFindings,
                        List<String> projectMastery,
                        List<String> knowledgeGaps,
                        List<String> studySuggestions) {}

    private List<String> readStrings(JsonNode root, String field) {
        JsonNode node = root.path(field);
        List<String> result = new ArrayList<>();
        if (!node.isArray()) {
            return result;
        }
        for (JsonNode item : node) {
            if (result.size() >= MAX_LIST_ITEMS) {
                break;
            }
            if (!item.isTextual() && !item.isNumber()) {
                continue;
            }
            String value = AiOutputCleaner.summarize(item.asText(), MAX_LIST_ITEM_CHARS);
            if (!value.isEmpty()) {
                result.add(value);
            }
        }
        return result;
    }

    private ApiException unstructured(String message) {
        return new ApiException(HttpStatus.BAD_GATEWAY, ErrorCode.INVALID_STRUCTURED_OUTPUT, message);
    }

    private String buildUserPrompt(InterviewSessionRecord session, List<InterviewTurnRecord> turns) {
        StringBuilder builder = new StringBuilder();
        builder.append("面试配置：标题=").append(session.getTitle())
                .append("，题材=").append(session.getTopicMode())
                .append("，形式=").append(session.getForm())
                .append("，轮次=").append(session.getRound())
                .append("，目标岗位=").append(session.getTargetRole() == null ? "未填写" : session.getTargetRole())
                .append("，目标年限=").append(session.getTargetExperienceBand() == null ? "未填写"
                        : session.getTargetExperienceBand())
                .append('\n');
        builder.append("问答记录（共 ").append(turns.size()).append(" 条）：\n");
        for (InterviewTurnRecord turn : turns) {
            builder.append("[").append(turn.getTurnNo()).append("][")
                    .append(turn.getTurnType()).append("] 问：")
                    .append(snippet(turn.getQuestion())).append('\n');
            if (turn.getAnswer() == null) {
                builder.append("    答：（未回答）\n");
            } else {
                builder.append("    答（来源 ").append(turn.getAnswerSource()).append("）：")
                        .append(snippet(turn.getAnswer())).append('\n');
            }
        }
        if (turns.isEmpty()) {
            builder.append("（该会话没有任何问答记录，请据实给出低分并说明原因）\n");
        }
        return builder.toString();
    }

    private String snippet(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= TURN_SNIPPET_LIMIT ? value : value.substring(0, TURN_SNIPPET_LIMIT) + "…";
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return null;
        }
    }

    private InterviewSessionRecord ownedSession(Long userId, Long sessionId) {
        InterviewSessionRecord session = sessionMapper.findById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "面试会话不存在");
        }
        return session;
    }

    private void requireGeneratableReport(Long sessionId) {
        InterviewReportRecord report = reportMapper.findBySessionId(sessionId);
        if (report == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "会话尚未结束，没有可生成的报告");
        }
        if (report.getStatus() != ReportStatus.REPORT_PENDING) {
            throw conflict("报告当前不可生成，请刷新后重试");
        }
    }

    private ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT, message);
    }
}
