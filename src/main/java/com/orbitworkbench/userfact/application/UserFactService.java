package com.orbitworkbench.userfact.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.ai.application.AiOutputCleaner;
import com.orbitworkbench.ai.application.PromptCatalog;
import com.orbitworkbench.aiconnection.application.AiScenarioExecutionService;
import com.orbitworkbench.aiconnection.domain.AiScenario;
import com.orbitworkbench.interview.domain.ReportCenterRow;
import com.orbitworkbench.interview.infrastructure.mapper.InterviewReportMapper;
import com.orbitworkbench.jobprofile.domain.JobProfileRecord;
import com.orbitworkbench.jobprofile.infrastructure.mapper.JobProfileMapper;
import com.orbitworkbench.learning.domain.LearningGoalRow;
import com.orbitworkbench.learning.infrastructure.mapper.LearningGoalMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.userfact.api.UserFactDtos.ConfirmUserFactRequest;
import com.orbitworkbench.userfact.api.UserFactDtos.CreateUserFactRequest;
import com.orbitworkbench.userfact.api.UserFactDtos.DistillResultResponse;
import com.orbitworkbench.userfact.api.UserFactDtos.UserFactListResponse;
import com.orbitworkbench.userfact.api.UserFactDtos.UserFactResponse;
import com.orbitworkbench.userfact.api.UserFactFreshness;
import com.orbitworkbench.userfact.domain.UserFactRecord;
import com.orbitworkbench.userfact.domain.UserFactSource;
import com.orbitworkbench.userfact.domain.UserFactStatus;
import com.orbitworkbench.userfact.infrastructure.mapper.UserFactMapper;
import com.orbitworkbench.worklog.domain.KnowledgeCardRow;
import com.orbitworkbench.worklog.domain.WorkLogRow;
import com.orbitworkbench.worklog.infrastructure.mapper.KnowledgeCardMapper;
import com.orbitworkbench.worklog.infrastructure.mapper.WorkLogMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户画像事实（个人记忆层，借鉴 EvoFlow 用户事实机制）。
 *
 * <p>沉淀刻意不自动跑：用户点「生成建议」才调 AI（USER_FACT 场景），
 * 产出先进 ANALYZED 候选池，用户确认（可顺手修正措辞）后才进入注入链路。
 * 与 project_fact 同一纪律：系统推断不得表述为用户亲自负责。
 *
 * <p>蒸馏的输入是「用户自己的材料」（求职档案 / 近期工作记录 / 知识卡片 /
 * 学习目标 / 近期面试结论），不上传原文、不读别人的数据；
 * 建议里若模型声明某条旧事实已被推翻，确认后按记录取代归档（SUPERSEDED），不物理删除。
 */
@Service
public class UserFactService {

    /** 正文见 resources/prompts/user-fact-distill-system.txt。 */
    private static final String DISTILL_SYSTEM_PROMPT = PromptCatalog.load("user-fact-distill-system");

    private static final Set<String> FACT_TYPES = Set.of(
            "PREFERENCE", "KNOWLEDGE", "CONTEXT", "BEHAVIOR", "GOAL", "OTHER");
    private static final Duration DISTILL_TIMEOUT = Duration.ofSeconds(90);
    private static final int DISTILL_MAX_TOKENS = 2000;
    private static final int MAX_SUGGESTIONS = 12;
    private static final int MIN_CONFIDENCE = 50;
    private static final int MATERIAL_CHAR_BUDGET = 20000;
    /** 注入预算：条数与单条字数都封顶，超限截断——记忆不能挤掉任务本身的上下文。 */
    private static final int INJECT_LIMIT = 10;
    private static final int INJECT_ITEM_MAX = 160;

    private final UserFactMapper factMapper;
    private final ProfileDigestService profileDigestService;
    private final JobProfileMapper jobProfileMapper;
    private final WorkLogMapper workLogMapper;
    private final KnowledgeCardMapper knowledgeCardMapper;
    private final LearningGoalMapper learningGoalMapper;
    private final InterviewReportMapper reportMapper;
    private final AiScenarioExecutionService aiScenarioExecution;
    private final ObjectMapper objectMapper;

    public UserFactService(UserFactMapper factMapper,
                           ProfileDigestService profileDigestService,
                           JobProfileMapper jobProfileMapper,
                           WorkLogMapper workLogMapper,
                           KnowledgeCardMapper knowledgeCardMapper,
                           LearningGoalMapper learningGoalMapper,
                           InterviewReportMapper reportMapper,
                           AiScenarioExecutionService aiScenarioExecution,
                           ObjectMapper objectMapper) {
        this.factMapper = factMapper;
        this.profileDigestService = profileDigestService;
        this.jobProfileMapper = jobProfileMapper;
        this.workLogMapper = workLogMapper;
        this.knowledgeCardMapper = knowledgeCardMapper;
        this.learningGoalMapper = learningGoalMapper;
        this.reportMapper = reportMapper;
        this.aiScenarioExecution = aiScenarioExecution;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public UserFactListResponse list(Long userId) {
        return new UserFactListResponse(factMapper.listByUser(userId).stream()
                .map(UserFactResponse::from).toList());
    }

    @Transactional
    public UserFactResponse createManual(Long userId, CreateUserFactRequest request) {
        String factType = normalizeType(request.factType());
        Instant now = Instant.now();
        UserFactRecord record = new UserFactRecord();
        record.setUserId(userId);
        record.setFactType(factType);
        record.setTitle(request.title().trim());
        record.setContent(request.content().trim());
        record.setSource(UserFactSource.USER_ENTERED);
        // 用户亲手写的事实即已确认，不再走候选池
        record.setConfirmationStatus(UserFactStatus.CONFIRMED);
        record.setConfidence(clampConfidence(request.confidence(), 100));
        record.setConfirmedAt(now);
        // last_seen_at 与 confirmed_at 同为「此刻由用户确认」——确认即首见
        record.setLastSeenAt(now);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        factMapper.insert(record);
        return UserFactResponse.from(factMapper.findByIdAndUser(record.getId(), userId));
    }

    /**
     * 复查（V43）：用户确认这条已确认事实「仍然成立」，把 last_seen_at 推到当下。
     * 与「确认候选」不同——它不改内容，只刷新时效，所以不需要重新编辑。
     */
    @Transactional
    public UserFactResponse reaffirm(Long userId, Long factId) {
        UserFactRecord record = requireOwned(userId, factId);
        if (record.getConfirmationStatus() != UserFactStatus.CONFIRMED) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "只有已确认的画像事实需要复查");
        }
        Instant now = Instant.now();
        if (factMapper.reaffirm(factId, userId, now, now) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT, "事实状态已变化");
        }
        return UserFactResponse.from(factMapper.findByIdAndUser(factId, userId));
    }

    @Transactional
    public UserFactResponse confirm(Long userId, Long factId, ConfirmUserFactRequest request) {
        UserFactRecord record = requireOwned(userId, factId);
        if (record.getConfirmationStatus() != UserFactStatus.ANALYZED) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT, "只有待确认的建议可以确认");
        }
        String factType = normalizeType(request.factType());
        Instant now = Instant.now();
        if (factMapper.confirm(factId, userId, factType, request.title().trim(),
                request.content().trim(), now, now) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT, "事实状态已变化");
        }
        // 建议里记录的取代关系在确认这一刻才生效：旧事实归档，注入链路不再看到两条矛盾记忆
        for (Long supersededId : parseSupersedes(record.getSourceHint())) {
            factMapper.archive(supersededId, userId, "SUPERSEDED", now);
        }
        return UserFactResponse.from(factMapper.findByIdAndUser(factId, userId));
    }

    @Transactional
    public UserFactResponse archive(Long userId, Long factId) {
        UserFactRecord record = requireOwned(userId, factId);
        if (record.getConfirmationStatus() == UserFactStatus.ARCHIVED) {
            return UserFactResponse.from(record);
        }
        factMapper.archive(factId, userId, "MANUAL", Instant.now());
        return UserFactResponse.from(factMapper.findByIdAndUser(factId, userId));
    }

    /**
     * 蒸馏建议：AI 在事务外调用（长耗时），解析与落库在短事务里。
     * 与知识蒸馏同一模式；已有候选未处理时拒绝重跑，避免建议池越堆越乱。
     */
    public DistillResultResponse distill(Long userId, Long connectionId) {
        if (factMapper.countAnalyzed(userId) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "还有未处理的画像建议，请先确认或忽略后再生成");
        }
        String material = buildMaterial(userId);
        if (material.isBlank()) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "还没有可用于沉淀画像的个人材料（先记录一些工作内容或跑一次面试）");
        }
        String output = aiScenarioExecution.executeText(AiScenario.USER_FACT, userId,
                connectionId, DISTILL_SYSTEM_PROMPT, material, DISTILL_MAX_TOKENS, DISTILL_TIMEOUT);
        return persistSuggestions(userId, output);
    }

    /** 注入链路统一读取口：优先用编译快照（V44），无快照或快照过期时逐条拼 CONFIRMED 事实。 */
    @Transactional(readOnly = true)
    public String confirmedContext(Long userId) {
        String digest = profileDigestService.freshDigestForInjection(userId);
        if (digest != null && !digest.isBlank()) {
            return "候选人画像快照（由其个人记忆层编译，反映已确认事实）：\n" + digest + '\n';
        }
        List<UserFactRecord> facts = factMapper.listConfirmed(userId, INJECT_LIMIT);
        if (facts.isEmpty()) {
            return "";
        }
        StringBuilder context = new StringBuilder("候选人已确认的画像事实（来自其个人记忆层）：\n");
        for (UserFactRecord fact : facts) {
            // 陈旧事实照常注入（用户没删就说明还有参考价值），但必须带上时效标注：
            // 三个月前确认的「Java 仅具备工作能力」不该被模型当成今天的水平。
            context.append("- [").append(fact.getFactType()).append("] ")
                    .append(AiOutputCleaner.truncate(
                            fact.getTitle() + "：" + fact.getContent(), INJECT_ITEM_MAX));
            String note = UserFactFreshness.injectNote(fact);
            if (!note.isEmpty()) {
                context.append(note);
            }
            context.append('\n');
        }
        return context.toString();
    }

    private DistillResultResponse persistSuggestions(Long userId, String output) {
        String json = AiOutputCleaner.extractJsonObject(output);
        if (json == null) {
            throw unstructured("画像建议输出中没有 JSON 对象");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (Exception exception) {
            throw unstructured("画像建议输出不是合法 JSON");
        }
        JsonNode facts = root.path("facts");
        if (!facts.isArray()) {
            throw unstructured("画像建议缺少 facts 数组");
        }
        Set<String> seen = new HashSet<>();
        collectKnown(userId, seen);
        // supersede 是全局声明（被新事实推翻的旧 CONFIRMED id），记在每条建议的 source_hint 上；
        // 确认任意一条建议时生效，archive 对已归档行不再命中，天然幂等。
        String supersedeHint = serializeSupersedes(readSupersedes(root.path("supersede")));
        List<UserFactRecord> inserted = new ArrayList<>();
        Instant now = Instant.now();
        int skippedLow = 0;
        for (JsonNode node : facts) {
            if (inserted.size() >= MAX_SUGGESTIONS) {
                break;
            }
            String factType = normalizeType(node.path("factType").asText(""));
            String title = AiOutputCleaner.summarize(node.path("title").asText(""), 120);
            String content = AiOutputCleaner.truncate(node.path("content").asText("").trim(), 800);
            int confidence = node.path("confidence").isNumber() ? node.path("confidence").asInt() : -1;
            if (!FACT_TYPES.contains(factType) || title.isBlank() || content.isBlank()) {
                // 单条脏输出丢弃即可：整体结构还在，不必像画像事实那样整批拒绝
                continue;
            }
            if (confidence >= 0 && confidence < MIN_CONFIDENCE) {
                skippedLow++;
                continue;
            }
            if (!seen.add(factType + "|" + content.toLowerCase())) {
                continue;
            }
            UserFactRecord record = new UserFactRecord();
            record.setUserId(userId);
            record.setFactType(factType);
            record.setTitle(title);
            record.setContent(content);
            record.setSource(UserFactSource.AI_SUGGESTED);
            record.setConfirmationStatus(UserFactStatus.ANALYZED);
            record.setConfidence(node.path("confidence").isNumber()
                    ? clampConfidence(node.path("confidence").asInt(), null) : null);
            record.setSourceHint(supersedeHint);
            record.setCreatedAt(now);
            record.setUpdatedAt(now);
            factMapper.insert(record);
            inserted.add(record);
        }
        if (inserted.isEmpty()) {
            throw unstructured("画像建议中没有有效条目");
        }
        return new DistillResultResponse(inserted.stream().map(UserFactResponse::from).toList(),
                skippedLow);
    }

    /** 模型声明被推翻的旧 CONFIRMED id，只接受数字元素。 */
    private List<Long> readSupersedes(JsonNode supersede) {
        if (!supersede.isArray() || supersede.isEmpty()) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (JsonNode node : supersede) {
            if (node.isNumber()) {
                ids.add(node.asLong());
            }
        }
        return ids;
    }

    private String serializeSupersedes(List<Long> ids) {
        if (ids.isEmpty()) {
            return null;
        }
        StringBuilder text = new StringBuilder("supersedes:");
        for (Long id : ids) {
            text.append(text.charAt(text.length() - 1) == ':' ? "" : ",").append(id);
        }
        return text.length() > 255 ? null : text.toString();
    }

    private List<Long> parseSupersedes(String sourceHint) {
        List<Long> ids = new ArrayList<>();
        if (sourceHint == null || !sourceHint.startsWith("supersedes:")) {
            return ids;
        }
        for (String part : sourceHint.substring("supersedes:".length()).split(",")) {
            try {
                ids.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException ignored) {
                // 手工数据脏了就跳过，不影响主流程
            }
        }
        return ids;
    }

    private void collectKnown(Long userId, Set<String> seen) {
        for (UserFactRecord fact : factMapper.listConfirmedForPrompt(userId)) {
            seen.add(fact.getFactType() + "|" + fact.getContent().toLowerCase());
        }
    }

    private String buildMaterial(Long userId) {
        StringBuilder material = new StringBuilder();
        JobProfileRecord profile = jobProfileMapper.findByUserId(userId);
        if (profile != null) {
            material.append("## 求职档案\n")
                    .append("目标岗位：").append(nullToDash(profile.getTargetRole()))
                    .append("；经验档位：").append(nullToDash(profile.getTargetExperienceBand()))
                    .append("；职业阶段：").append(nullToDash(profile.getCareerStage()))
                    .append("；目标级别：").append(nullToDash(profile.getTargetLevel()))
                    .append("；Java 水平：").append(nullToDash(profile.getJavaSkillLevel()))
                    .append("；AI 水平：").append(nullToDash(profile.getAiSkillLevel()))
                    .append('\n');
        }
        appendSection(material, "## 近期工作记录", workLogMapper.listByUser(userId, 15, 0).stream()
                .map(row -> "- [" + row.getCategory() + "] " + clip(row.getTitle() + "：" + row.getContent()))
                .toList());
        appendSection(material, "## 近期知识卡片", knowledgeCardMapper.listByUser(userId, 15, 0).stream()
                .map(row -> "- " + clip(row.getTitle() + "：" + row.getSummary()))
                .toList());
        appendSection(material, "## 学习目标", learningGoalMapper.listByUser(userId, 10, 0).stream()
                .map(row -> "- [" + row.getStatus() + "] " + clip(row.getTitle()
                        + (row.getReason() == null ? "" : "（" + row.getReason() + "）")))
                .toList());
        appendSection(material, "## 近期面试结论", reportMapper
                .listByUser(userId, null, null, null, null, null, 5, 0).stream()
                .filter(row -> "REPORT_READY".equals(row.getReportStatus()))
                .map(this::interviewDigest)
                .filter(text -> !text.isBlank())
                .toList());
        if (material.length() > MATERIAL_CHAR_BUDGET) {
            material.setLength(MATERIAL_CHAR_BUDGET);
        }
        return material.toString();
    }

    private String interviewDigest(ReportCenterRow row) {
        StringBuilder line = new StringBuilder("- ").append(row.getSessionTitle())
                .append("：总分 ").append(row.getTotalScore() == null ? "—" : row.getTotalScore())
                .append("，建议 ").append(nullToDash(row.getHiringRecommendation()));
        String gaps = firstItems(row.getKnowledgeGapsJson(), 2);
        if (!gaps.isBlank()) {
            line.append("，知识缺口 ").append(gaps);
        }
        return line.toString();
    }

    private void appendSection(StringBuilder material, String heading, List<String> lines) {
        if (lines.isEmpty()) {
            return;
        }
        material.append(heading).append('\n');
        lines.forEach(line -> material.append(line).append('\n'));
    }

    private String firstItems(String jsonArray, int limit) {
        if (jsonArray == null || jsonArray.isBlank()) {
            return "";
        }
        try {
            JsonNode node = objectMapper.readTree(jsonArray);
            if (!node.isArray()) {
                return "";
            }
            List<String> items = new ArrayList<>();
            for (JsonNode item : node) {
                if (items.size() >= limit) {
                    break;
                }
                String text = item.asText("").trim();
                if (!text.isEmpty()) {
                    items.add(clip(text));
                }
            }
            return String.join("；", items);
        } catch (Exception exception) {
            return "";
        }
    }

    private static String clip(String text) {
        String flat = text.replace('\n', ' ').trim();
        return flat.length() <= 200 ? flat : flat.substring(0, 200) + "…";
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private static String normalizeType(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase();
    }

    private static Integer clampConfidence(Integer value, Integer defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return Math.max(0, Math.min(100, value));
    }

    private UserFactRecord requireOwned(Long userId, Long factId) {
        UserFactRecord record = factMapper.findByIdAndUser(factId, userId);
        if (record == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "用户事实不存在");
        }
        return record;
    }

    private static ApiException unstructured(String reason) {
        return new ApiException(HttpStatus.BAD_GATEWAY, ErrorCode.UPSTREAM_UNAVAILABLE,
                "画像沉淀失败：" + reason);
    }
}
