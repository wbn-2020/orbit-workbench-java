package com.orbitworkbench.interview.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.ai.application.AiCallFailures;
import com.orbitworkbench.ai.application.AiInvocation;
import com.orbitworkbench.ai.application.AiOutputCleaner;
import com.orbitworkbench.ai.application.AiStreamEvent;
import com.orbitworkbench.ai.application.ModelGateway;
import com.orbitworkbench.ai.application.WebSearchDecision;
import com.orbitworkbench.ai.application.WebSearchMode;
import com.orbitworkbench.aiconnection.application.AiCallAuditRecorder;
import com.orbitworkbench.aiconnection.application.AiScenarioExecutionService;
import com.orbitworkbench.aiconnection.application.AiScenarioRouter;
import com.orbitworkbench.aiconnection.domain.AiScenario;
import com.orbitworkbench.interview.api.InterviewDtos.NextQuestionRequest;
import com.orbitworkbench.interview.api.InterviewDtos.TurnResponse;
import com.orbitworkbench.interview.domain.InterviewSessionRecord;
import com.orbitworkbench.interview.domain.InterviewSessionStatus;
import com.orbitworkbench.interview.domain.InterviewTurnRecord;
import com.orbitworkbench.interview.domain.InterviewTurnType;
import com.orbitworkbench.interview.infrastructure.mapper.InterviewSessionMapper;
import com.orbitworkbench.interview.infrastructure.mapper.InterviewTurnMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.shared.api.RequestEnums;
import java.time.Duration;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * AI 出题（主问题/动态追问）。
 *
 * <p>会话须处于 RUNNING；MAIN 消耗主问题与总问答预算，FOLLOW_UP 消耗追问与总问答预算，
 * 且要求最后一条问答已回答（追问必须针对已完成的回答）。题目由模型生成并持久化为
 * interview_turn；模型只输出题目本身，经长度校验后入库。阻塞出题走
 * {@code INTERVIEW_QUESTION} 场景（含调用审计与备用切换）；SSE 流式出题共用同一场景的
 * 账户选择结果，但不做备用切换——流已开始下发后切换账户会造成题目拼接错乱。</p>
 */
@Service
public class InterviewQuestionService {

    private static final Duration MODEL_TIMEOUT = Duration.ofSeconds(120);
    private static final int MAX_OUTPUT_TOKENS = 512;
    private static final int QUESTION_MIN_LENGTH = 8;
    private static final int QUESTION_MAX_LENGTH = 800;
    private static final int CONTEXT_SNIPPET_LIMIT = 600;

    private final InterviewSessionMapper sessionMapper;
    private final InterviewTurnMapper turnMapper;
    private final AiScenarioRouter aiScenarioRouter;
    private final AiScenarioExecutionService aiScenarioExecution;
    private final AiCallAuditRecorder aiCallAuditRecorder;
    private final ModelGateway modelGateway;
    private final ObjectMapper objectMapper;
    private final InterviewTurnWriteService turnWriteService;

    public InterviewQuestionService(InterviewSessionMapper sessionMapper,
                                    InterviewTurnMapper turnMapper,
                                    AiScenarioRouter aiScenarioRouter,
                                    AiScenarioExecutionService aiScenarioExecution,
                                    AiCallAuditRecorder aiCallAuditRecorder,
                                    ModelGateway modelGateway,
                                    ObjectMapper objectMapper,
                                    InterviewTurnWriteService turnWriteService) {
        this.sessionMapper = sessionMapper;
        this.turnMapper = turnMapper;
        this.aiScenarioRouter = aiScenarioRouter;
        this.aiScenarioExecution = aiScenarioExecution;
        this.aiCallAuditRecorder = aiCallAuditRecorder;
        this.modelGateway = modelGateway;
        this.objectMapper = objectMapper;
        this.turnWriteService = turnWriteService;
    }

    public TurnResponse next(Long userId, Long sessionId, NextQuestionRequest request) {
        InterviewSessionRecord session = requireOwned(userId, sessionId);
        requireRunning(session);
        InterviewTurnType turnType = RequestEnums.parse(InterviewTurnType.class,
                request.turnType(), "turnType");
        checkBudget(session, turnType);
        if (turnType == InterviewTurnType.FOLLOW_UP) {
            requireLastAnswered(session.getId());
        }

        List<InterviewTurnRecord> history = turnMapper.listBySession(session.getId());
        PromptPair pair = buildPrompt(session, history, turnType, request.instruction());
        int expectedTurnCount = history.size();
        String question = requireQuestion(aiScenarioExecution.executeText(
                AiScenario.INTERVIEW_QUESTION, userId,
                session.getAiConnectionIdSnapshot(), pair.system(), pair.user(),
                MAX_OUTPUT_TOKENS, MODEL_TIMEOUT));

        InterviewTurnRecord turn = turnWriteService.append(userId, sessionId, turnType,
                question, expectedTurnCount);
        return TurnResponse.from(turnMapper.findById(turn.getId()));
    }

    /**
     * 流式出题：SSE 事件流 start -> delta* -> done（题目落库后返回 turnId）。
     * 会话/预算校验失败同步抛 ApiException；模型侧失败以 error 事件下发后正常收流，
     * 且失败时不落库半成品题目。
     */
    public Flux<ServerSentEvent<String>> streamNext(Long userId, Long sessionId,
                                                    NextQuestionRequest request) {
        InterviewSessionRecord session = requireOwned(userId, sessionId);
        requireRunning(session);
        InterviewTurnType turnType = RequestEnums.parse(InterviewTurnType.class,
                request.turnType(), "turnType");
        checkBudget(session, turnType);
        if (turnType == InterviewTurnType.FOLLOW_UP) {
            requireLastAnswered(session.getId());
        }
        AiScenarioRouter.ResolvedRoute route = aiScenarioRouter.resolve(
                userId, AiScenario.INTERVIEW_QUESTION, session.getAiConnectionIdSnapshot());
        // 联网结论在开流之前算：「必须联网」而这条连接做不到时要回一个干净的 422，
        // 不能先推半个 SSE 再报错——那时题号已经发给前端了。
        WebSearchDecision webSearch = WebSearchDecision.resolve(
                WebSearchMode.parse(session.getWebSearchPolicy()), route.primary());
        // 结论当场固化到会话：连接的联网形状以后可能被改，历史会话不能跟着被改写（V32）。
        sessionMapper.updateWebSearchOutcome(session.getId(),
                route.primary().webSearchDialect().name(), webSearch.outcome(), webSearch.reason());
        List<InterviewTurnRecord> history = turnMapper.listBySession(session.getId());
        PromptPair pair = buildPrompt(session, history, turnType, request.instruction());
        Long sessionIdValue = session.getId();

        return Flux.defer(() -> streamEvents(userId, sessionIdValue, turnType, route, pair,
                webSearch, history.size()));
    }

    private Flux<ServerSentEvent<String>> streamEvents(Long userId, Long sessionId,
                                                       InterviewTurnType turnType,
                                                       AiScenarioRouter.ResolvedRoute route,
                                                       PromptPair pair,
                                                       WebSearchDecision webSearch,
                                                       int expectedTurnCount) {
        StringBuilder buffer = new StringBuilder();
        int requestChars = pair.system().length() + pair.user().length();
        long start = System.nanoTime();
        Long auditId = aiCallAuditRecorder.start(userId, AiScenario.INTERVIEW_QUESTION, route,
                requestChars, aiCallAuditRecorder.snapshotJson(AiScenario.INTERVIEW_QUESTION, route,
                        MAX_OUTPUT_TOKENS, true, webSearch));
        int turnNo = expectedTurnCount + 1;
        ServerSentEvent<String> startEvent = sse("start", "{\"turnNo\":" + turnNo
                + ",\"type\":\"" + turnType + "\"}");
        // usage 由适配器在流尾上报；留最后一次，供审计与费用账本落库
        java.util.concurrent.atomic.AtomicReference<com.orbitworkbench.ai.application.AiUsage> usage =
                new java.util.concurrent.atomic.AtomicReference<>();
        Flux<ServerSentEvent<String>> body = modelGateway
                .stream(new AiInvocation(route.primary(), pair.system(), pair.user(),
                        null, null, true, MAX_OUTPUT_TOKENS).withWebSearch(webSearch.effective()))
                .doOnNext(event -> {
                    if (event.usage() != null) {
                        usage.set(event.usage());
                    }
                })
                .mapNotNull(AiStreamEvent::text)
                .map(text -> {
                    buffer.append(text);
                    return sse("delta", text);
                });
        Flux<ServerSentEvent<String>> tail = Flux.defer(() -> {
            String question = AiOutputCleaner.cleanQuestion(buffer.toString(), QUESTION_MAX_LENGTH);
            if (question.length() < QUESTION_MIN_LENGTH) {
                aiCallAuditRecorder.finish(auditId, userId, AiScenario.INTERVIEW_QUESTION,
                        AiCallAuditRecorder.STATUS_FAILED,
                        ErrorCode.INVALID_STRUCTURED_OUTPUT.name(), elapsedMillis(start),
                        requestChars, buffer.length(),
                        usageOf(usage), outputTokensOf(usage),
                        AiCallAuditRecorder.cost(route.primary(), usageOf(usage), outputTokensOf(usage)),
                        route.primary().connectionId(), false);
                return Flux.just(sse("error", "面试出题未完成：模型未返回有效题目"));
            }
            InterviewTurnRecord turn = turnWriteService.append(userId, sessionId, turnType,
                    question, turnNo - 1);
            aiCallAuditRecorder.finish(auditId, userId, AiScenario.INTERVIEW_QUESTION,
                    AiCallAuditRecorder.STATUS_SUCCEEDED, null, elapsedMillis(start),
                    requestChars, question.length(),
                    usageOf(usage), outputTokensOf(usage),
                    AiCallAuditRecorder.cost(route.primary(), usageOf(usage), outputTokensOf(usage)),
                    route.primary().connectionId(), false);
            String payload = "{\"turnId\":" + turn.getId() + ",\"turnNo\":" + turn.getTurnNo()
                    + ",\"question\":\"" + jsonEscape(question) + "\"}";
            return Flux.just(sse("done", payload));
        });
        return Flux.concat(Flux.just(startEvent), body, tail)
                .onErrorResume(failure -> {
                    ApiException mapped = AiCallFailures.toApiException(
                            AiScenario.INTERVIEW_QUESTION.label(), failure);
                    aiCallAuditRecorder.finish(auditId, userId, AiScenario.INTERVIEW_QUESTION,
                            AiCallAuditRecorder.STATUS_FAILED, mapped.getErrorCode().name(),
                            elapsedMillis(start), requestChars, buffer.length(),
                            usageOf(usage), outputTokensOf(usage),
                            AiCallAuditRecorder.cost(route.primary(), usageOf(usage), outputTokensOf(usage)),
                            route.primary().connectionId(), false);
                    return Flux.just(sse("error", mapped.getMessage()));
                });
    }

    private static Integer usageOf(java.util.concurrent.atomic.AtomicReference<com.orbitworkbench.ai.application.AiUsage> ref) {
        return ref.get() == null ? null : ref.get().inputTokens();
    }

    private static Integer outputTokensOf(java.util.concurrent.atomic.AtomicReference<com.orbitworkbench.ai.application.AiUsage> ref) {
        return ref.get() == null ? null : ref.get().outputTokens();
    }

    private int elapsedMillis(long start) {
        return Math.max(0, (int) ((System.nanoTime() - start) / 1_000_000L));
    }

    private InterviewSessionRecord requireOwned(Long userId, Long sessionId) {
        InterviewSessionRecord session = sessionMapper.findById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "面试会话不存在");
        }
        return session;
    }

    private void requireRunning(InterviewSessionRecord session) {
        if (session.getStatus() != InterviewSessionStatus.RUNNING) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "会话未在进行中，无法出题：当前 " + session.getStatus());
        }
    }

    private void checkBudget(InterviewSessionRecord session, InterviewTurnType turnType) {
        if (turnMapper.countBySession(session.getId()) >= session.getTurnLimit()) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "总问答数已达上限（" + session.getTurnLimit() + "）");
        }
        if (turnType == InterviewTurnType.MAIN
                && turnMapper.countBySessionAndType(session.getId(), InterviewTurnType.MAIN)
                        >= session.getQuestionLimit()) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "主问题数量已达上限（" + session.getQuestionLimit() + "）");
        }
        if (turnType == InterviewTurnType.FOLLOW_UP
                && turnMapper.countBySessionAndType(session.getId(), InterviewTurnType.FOLLOW_UP)
                        >= session.getFollowUpLimit()) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "追问数量已达上限（" + session.getFollowUpLimit() + "）");
        }
    }

    private void requireLastAnswered(Long sessionId) {
        List<InterviewTurnRecord> turns = turnMapper.listBySession(sessionId);
        if (turns.isEmpty() || turns.get(turns.size() - 1).getAnswer() == null) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "追问必须基于最后一条已回答的问题");
        }
    }

    private PromptPair buildPrompt(InterviewSessionRecord session, List<InterviewTurnRecord> turns,
                                   InterviewTurnType turnType, String instruction) {
        String role = session.getInterviewerNameSnapshot() == null
                ? "资深面试官" : session.getInterviewerNameSnapshot();
        String system = "你是" + role + "，正在面试一位目标岗位为"
                + (session.getTargetRole() == null ? "Java 后端工程师" : session.getTargetRole())
                + "、经验档位为"
                + (session.getTargetExperienceBand() == null ? "未注明" : session.getTargetExperienceBand())
                + "的候选人。面试题材：" + session.getTopicMode() + "；形式：" + session.getForm()
                + "。只输出一个面试问题的原文：不要编号、不要解释、不要输出参考答案或评分。"
                + "问题长度控制在 8 到 300 字。";
        String persona = interviewerPersona(session.getInterviewerSnapshotJson());
        if (!persona.isEmpty()) {
            system = system + "\n" + persona;
        }
        StringBuilder user = new StringBuilder();
        String projectContext = projectBindingsContext(session.getProjectBindingsJson());
        if (!projectContext.isEmpty()) {
            user.append(projectContext).append('\n');
        }
        String knowledgeContext = knowledgeBindingsContext(session.getKnowledgeBindingsJson());
        if (!knowledgeContext.isEmpty()) {
            user.append(knowledgeContext).append('\n');
        }
        user.append("已进行的问答（可为空）：\n");
        for (InterviewTurnRecord turn : turns) {
            user.append("[").append(turn.getTurnType()).append("] 问：")
                    .append(snippet(turn.getQuestion())).append('\n');
            user.append("答（").append(turn.getAnswerSource() == null ? "未回答"
                            : turn.getAnswerSource()).append("）：")
                    .append(snippet(turn.getAnswer())).append('\n');
        }
        if (turnType == InterviewTurnType.MAIN) {
            user.append("请提出下一道主问题，考查方向不得与已有主问题重复。");
        } else {
            user.append("请针对候选人最后一条回答中的薄弱点、边界或未展开处提出一个追问。");
        }
        if (instruction != null && !instruction.isBlank()) {
            user.append("补充要求：").append(instruction.trim());
        }
        return new PromptPair(system, user.toString());
    }

    /** 从面试官快照提取人设与重点考查方向；旧会话无快照时为空。 */
    private String interviewerPersona(String snapshotJson) {
        if (snapshotJson == null || snapshotJson.isBlank()) {
            return "";
        }
        try {
            JsonNode node = objectMapper.readTree(snapshotJson);
            StringBuilder persona = new StringBuilder();
            JsonNode prompt = node.get("systemPrompt");
            if (prompt != null && prompt.isTextual() && !prompt.asText().isBlank()) {
                persona.append("面试官人设与要求：").append(prompt.asText().trim());
            }
            JsonNode tags = node.get("focusTags");
            if (tags != null && tags.isArray() && !tags.isEmpty()) {
                StringBuilder joined = new StringBuilder();
                for (JsonNode tag : tags) {
                    if (tag.isTextual() && !tag.asText().isBlank()) {
                        if (!joined.isEmpty()) {
                            joined.append("、");
                        }
                        joined.append(tag.asText().trim());
                    }
                }
                if (!joined.isEmpty()) {
                    if (!persona.isEmpty()) {
                        persona.append(' ');
                    }
                    persona.append("重点考查方向：").append(joined).append("。");
                }
            }
            return persona.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    /**
     * 从知识卡片快照提取工作心得，作为可追问的真实经验依据（23 号设计 A 案）。
     * 与项目事实分段呈现，各说各话；无快照或解析失败为空，不让脏数据进 prompt。
     */
    private String knowledgeBindingsContext(String bindingsJson) {
        if (bindingsJson == null || bindingsJson.isBlank()) {
            return "";
        }
        try {
            JsonNode cards = objectMapper.readTree(bindingsJson);
            if (!cards.isArray() || cards.isEmpty()) {
                return "";
            }
            StringBuilder context = new StringBuilder(
                    "以下是候选人从工作记录中蒸馏出的个人经验（知识卡片），可以直接围绕这些真实经历追问实现细节与权衡，"
                            + "不要向候选人复述原文：\n");
            int count = 0;
            for (JsonNode card : cards) {
                if (count >= 5) {
                    break;
                }
                count++;
                context.append("- ").append(textOr(card.get("title"), "经验"))
                        .append("：").append(limit(textOr(card.get("summary"), ""), 300))
                        .append('\n');
            }
            return context.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    /** 从项目绑定快照提取已确认画像事实，作为项目类提问依据；无绑定或解析失败为空。 */
    private String projectBindingsContext(String bindingsJson) {
        if (bindingsJson == null || bindingsJson.isBlank()) {
            return "";
        }
        try {
            JsonNode bindings = objectMapper.readTree(bindingsJson);
            if (!bindings.isArray() || bindings.isEmpty()) {
                return "";
            }
            StringBuilder context = new StringBuilder(
                    "以下是会话绑定的项目资料快照（已确认画像事实），仅作为提问依据，不要向候选人复述原文：\n");
            int bindingCount = 0;
            for (JsonNode binding : bindings) {
                if (bindingCount >= 3) {
                    break;
                }
                bindingCount++;
                context.append("项目：").append(textOr(binding.get("projectName"), "未命名"))
                        .append("（V").append(binding.path("versionNumber").asInt(0)).append("）\n");
                JsonNode facts = binding.get("facts");
                int factCount = 0;
                if (facts != null && facts.isArray()) {
                    for (JsonNode fact : facts) {
                        if (factCount >= 3) {
                            break;
                        }
                        factCount++;
                        context.append("- ").append(textOr(fact.get("title"), "事实"))
                                .append("：").append(limit(textOr(fact.get("content"), ""), 300))
                                .append('\n');
                    }
                }
            }
            return context.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private String textOr(JsonNode node, String fallback) {
        return node != null && node.isTextual() && !node.asText().isBlank()
                ? node.asText().trim() : fallback;
    }

    private String limit(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "…";
    }

    private record PromptPair(String system, String user) {}

    private String requireQuestion(String rawOutput) {
        String question = AiOutputCleaner.cleanQuestion(rawOutput, QUESTION_MAX_LENGTH);
        if (question.length() < QUESTION_MIN_LENGTH) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, ErrorCode.INVALID_STRUCTURED_OUTPUT,
                    "模型未返回有效题目");
        }
        return question;
    }

    private ServerSentEvent<String> sse(String event, String data) {
        return ServerSentEvent.builder(data).event(event).build();
    }

    private String jsonEscape(String value) {
        StringBuilder escaped = new StringBuilder();
        value.chars().forEach(codePoint -> {
            char c = (char) codePoint;
            switch (c) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\n' -> escaped.append("\\n");
                case '\r' -> { /* 丢弃 */ }
                default -> escaped.append(c);
            }
        });
        return escaped.toString();
    }

    private String snippet(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= CONTEXT_SNIPPET_LIMIT
                ? value : value.substring(0, CONTEXT_SNIPPET_LIMIT) + "…";
    }
}
