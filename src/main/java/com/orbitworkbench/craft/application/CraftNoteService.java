package com.orbitworkbench.craft.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.ai.application.AiOutputCleaner;
import com.orbitworkbench.ai.application.PromptCatalog;
import com.orbitworkbench.aiconnection.application.AiScenarioExecutionService;
import com.orbitworkbench.aiconnection.domain.AiScenario;
import com.orbitworkbench.craft.api.CraftDtos.CraftNoteResponse;
import com.orbitworkbench.craft.api.CraftDtos.SaveCraftRequest;
import com.orbitworkbench.craft.domain.CraftNoteRecord;
import com.orbitworkbench.craft.domain.CraftSource;
import com.orbitworkbench.craft.domain.CraftStatus;
import com.orbitworkbench.craft.infrastructure.mapper.CraftNoteMapper;
import com.orbitworkbench.interview.domain.ReportCenterRow;
import com.orbitworkbench.interview.infrastructure.mapper.InterviewReportMapper;
import com.orbitworkbench.jobprofile.domain.JobProfileRecord;
import com.orbitworkbench.jobprofile.infrastructure.mapper.JobProfileMapper;
import com.orbitworkbench.learning.domain.LearningGoalRow;
import com.orbitworkbench.learning.infrastructure.mapper.LearningGoalMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.userfact.infrastructure.mapper.UserFactMapper;
import com.orbitworkbench.worklog.domain.KnowledgeCardRow;
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
 * 可复用「本事」库（craft，V47，借鉴 EvoFlow 资产中心 craft / 晋升为技能）。
 *
 * <p>与知识块的分工：知识块回答「这个技术点是什么」，craft 回答「我怎么做这类事」。
 * 与画像事实的分工：事实描述「我是谁」（注入 AI 对话），craft 是可照做的套路
 * （刻意不注入——套路是候选人自己的底牌，不该告诉面试官）。
 *
 * <p>产物纪律同画像沉淀：AI 只能给建议（ANALYZED 候选池），用户确认后才进库。
 */
@Service
public class CraftNoteService {

    /** 正文见 resources/prompts/craft-distill-system.txt。 */
    private static final String DISTILL_SYSTEM_PROMPT = PromptCatalog.load("craft-distill-system");

    private static final Set<String> CATEGORIES = Set.of(
            "STORY", "SCRIPT", "PLAYBOOK", "REVIEW", "OTHER");
    /**
     * 提炼比其它场景重：一次要产出最多 4 条「标题 + 适用场景 + 分步正文」的结构化套路，
     * 推理型模型 90s 不够（实测网关抖动时 90s 直接超时），给到 180s。
     */
    private static final Duration DISTILL_TIMEOUT = Duration.ofSeconds(180);
    private static final int DISTILL_MAX_TOKENS = 2000;
    private static final int MAX_SUGGESTIONS = 4;
    private static final int MIN_CONFIDENCE = 60;
    private static final int MATERIAL_CHAR_BUDGET = 20000;
    static final int MAX_TAGS = 8;
    static final int MAX_TAG_CHARS = 64;

    private final CraftNoteMapper craftMapper;
    private final JobProfileMapper jobProfileMapper;
    private final WorkLogMapper workLogMapper;
    private final KnowledgeCardMapper knowledgeCardMapper;
    private final LearningGoalMapper learningGoalMapper;
    private final InterviewReportMapper reportMapper;
    private final UserFactMapper userFactMapper;
    private final AiScenarioExecutionService aiScenarioExecution;
    private final ObjectMapper objectMapper;

    public CraftNoteService(CraftNoteMapper craftMapper,
                            JobProfileMapper jobProfileMapper,
                            WorkLogMapper workLogMapper,
                            KnowledgeCardMapper knowledgeCardMapper,
                            LearningGoalMapper learningGoalMapper,
                            InterviewReportMapper reportMapper,
                            UserFactMapper userFactMapper,
                            AiScenarioExecutionService aiScenarioExecution,
                            ObjectMapper objectMapper) {
        this.craftMapper = craftMapper;
        this.jobProfileMapper = jobProfileMapper;
        this.workLogMapper = workLogMapper;
        this.knowledgeCardMapper = knowledgeCardMapper;
        this.learningGoalMapper = learningGoalMapper;
        this.reportMapper = reportMapper;
        this.userFactMapper = userFactMapper;
        this.aiScenarioExecution = aiScenarioExecution;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<CraftNoteResponse> list(Long userId) {
        return craftMapper.listByUser(userId).stream()
                .map(record -> CraftNoteResponse.from(record, parseTags(record.getTagsJson())))
                .toList();
    }

    /** 手动录入：用户亲手写的套路即已确认，不进候选池。 */
    @Transactional
    public CraftNoteResponse createManual(Long userId, SaveCraftRequest request) {
        Instant now = Instant.now();
        CraftNoteRecord record = new CraftNoteRecord();
        record.setUserId(userId);
        record.setCategory(normalizeCategory(request.category()));
        record.setTitle(request.title().trim());
        record.setWhenToUse(request.whenToUse().trim());
        record.setContent(request.content().trim());
        record.setTagsJson(serializeTags(request.tags()));
        record.setSource(CraftSource.USER_ENTERED);
        record.setConfirmationStatus(CraftStatus.CONFIRMED);
        record.setPinned(false);
        record.setVersion(1);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        craftMapper.insert(record);
        return toResponse(craftMapper.findByIdAndUser(record.getId(), userId));
    }

    /** 编辑：已确认或候选都可改（改即视为用户接管措辞）。 */
    @Transactional
    public CraftNoteResponse update(Long userId, Long id, SaveCraftRequest request) {
        CraftNoteRecord record = requireOwned(userId, id);
        if (record.getConfirmationStatus() == CraftStatus.ARCHIVED) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT, "已归档的套路不能编辑");
        }
        craftMapper.updateContent(id, userId, normalizeCategory(request.category()),
                request.title().trim(), request.whenToUse().trim(), request.content().trim(),
                serializeTags(request.tags()), Instant.now());
        return toResponse(craftMapper.findByIdAndUser(id, userId));
    }

    /** 确认 AI 建议：ANALYZED -> CONFIRMED。 */
    @Transactional
    public CraftNoteResponse confirm(Long userId, Long id, SaveCraftRequest request) {
        CraftNoteRecord record = requireOwned(userId, id);
        if (record.getConfirmationStatus() != CraftStatus.ANALYZED) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT, "只有待确认的建议可以确认");
        }
        if (craftMapper.confirm(id, userId, normalizeCategory(request.category()),
                request.title().trim(), request.whenToUse().trim(), request.content().trim(),
                serializeTags(request.tags()), Instant.now()) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT, "套路状态已变化");
        }
        return toResponse(craftMapper.findByIdAndUser(id, userId));
    }

    @Transactional
    public CraftNoteResponse archive(Long userId, Long id) {
        CraftNoteRecord record = requireOwned(userId, id);
        if (record.getConfirmationStatus() == CraftStatus.ARCHIVED) {
            return toResponse(record);
        }
        craftMapper.archive(id, userId, Instant.now());
        return toResponse(craftMapper.findByIdAndUser(id, userId));
    }

    @Transactional
    public CraftNoteResponse setPinned(Long userId, Long id, boolean pinned) {
        CraftNoteRecord record = requireOwned(userId, id);
        if (record.getConfirmationStatus() != CraftStatus.CONFIRMED) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT, "只有已确认的套路可以置顶");
        }
        craftMapper.setPinned(id, userId, pinned, Instant.now());
        return toResponse(craftMapper.findByIdAndUser(id, userId));
    }

    /**
     * 蒸馏建议：AI 在事务外调用（长耗时），解析与落库在短事务里，
     * 与画像沉淀同一模式；已有候选未处理时拒绝重跑。
     */
    public List<CraftNoteResponse> distill(Long userId, Long connectionId) {
        if (craftMapper.countAnalyzed(userId) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "还有未处理的套路建议，请先确认或忽略后再提炼");
        }
        String material = buildMaterial(userId);
        if (material.isBlank()) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "还没有可用于提炼的个人材料（先记录一些工作内容或跑一次面试）");
        }
        String output = aiScenarioExecution.executeText(AiScenario.CRAFT_DISTILL, userId,
                connectionId, DISTILL_SYSTEM_PROMPT, material, DISTILL_MAX_TOKENS, DISTILL_TIMEOUT);
        return persistSuggestions(userId, output);
    }

    private List<CraftNoteResponse> persistSuggestions(Long userId, String output) {
        String json = AiOutputCleaner.extractJsonObject(output);
        if (json == null) {
            throw unstructured("套路提炼输出中没有 JSON 对象");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (Exception exception) {
            throw unstructured("套路提炼输出不是合法 JSON");
        }
        JsonNode crafts = root.path("crafts");
        if (!crafts.isArray()) {
            throw unstructured("套路提炼输出缺少 crafts 数组");
        }
        Set<String> seen = new HashSet<>();
        for (CraftNoteRecord existing : craftMapper.listConfirmedForPrompt(userId)) {
            seen.add(existing.getTitle().trim().toLowerCase());
        }
        List<CraftNoteRecord> inserted = new ArrayList<>();
        Instant now = Instant.now();
        for (JsonNode node : crafts) {
            if (inserted.size() >= MAX_SUGGESTIONS) {
                break;
            }
            String category = normalizeCategory(node.path("category").asText(""));
            String title = AiOutputCleaner.summarize(node.path("title").asText(""), 120);
            String whenToUse = AiOutputCleaner.summarize(node.path("whenToUse").asText(""), 255);
            String content = AiOutputCleaner.truncate(node.path("content").asText("").trim(), 2000);
            int confidence = node.path("confidence").isNumber() ? node.path("confidence").asInt() : -1;
            if (!CATEGORIES.contains(category) || title.isBlank() || whenToUse.isBlank()
                    || content.isBlank()) {
                continue;
            }
            if (confidence >= 0 && confidence < MIN_CONFIDENCE) {
                continue;
            }
            if (!seen.add(title.toLowerCase())) {
                continue;
            }
            CraftNoteRecord record = new CraftNoteRecord();
            record.setUserId(userId);
            record.setCategory(category);
            record.setTitle(title);
            record.setWhenToUse(whenToUse);
            record.setContent(content);
            record.setTagsJson("[]");
            record.setSource(CraftSource.AI_SUGGESTED);
            record.setConfirmationStatus(CraftStatus.ANALYZED);
            record.setConfidence(node.path("confidence").isNumber()
                    ? clampConfidence(node.path("confidence").asInt()) : null);
            record.setPinned(false);
            record.setVersion(1);
            record.setCreatedAt(now);
            record.setUpdatedAt(now);
            craftMapper.insert(record);
            inserted.add(record);
        }
        if (inserted.isEmpty()) {
            throw unstructured("套路建议中没有有效条目");
        }
        return inserted.stream()
                .map(record -> CraftNoteResponse.from(record, List.of()))
                .toList();
    }

    /** 提炼材料与画像沉淀同源：都是「用户自己的材料」，只多带已确认事实与已有套路对比。 */
    private String buildMaterial(Long userId) {
        StringBuilder material = new StringBuilder();
        JobProfileRecord profile = jobProfileMapper.findByUserId(userId);
        if (profile != null) {
            material.append("## 求职档案\n")
                    .append("目标岗位：").append(nullToDash(profile.getTargetRole()))
                    .append("；经验档位：").append(nullToDash(profile.getTargetExperienceBand()))
                    .append("；职业阶段：").append(nullToDash(profile.getCareerStage()))
                    .append("；目标级别：").append(nullToDash(profile.getTargetLevel()))
                    .append('\n');
        }
        appendSection(material, "## 近期工作记录", workLogMapper.listByUser(userId, 15, 0).stream()
                .map(row -> "- [" + row.getCategory() + "] " + clip(row.getTitle() + "：" + row.getContent()))
                .toList());
        appendSection(material, "## 近期知识卡片", knowledgeCardMapper.listByUser(userId, 15, 0).stream()
                .map(row -> "- " + clip(row.getTitle() + "：" + row.getSummary()))
                .toList());
        appendSection(material, "## 学习目标", learningGoalMapper.listByUser(userId, 10, 0).stream()
                .map(row -> "- [" + row.getStatus() + "] " + clip(row.getTitle()))
                .toList());
        appendSection(material, "## 近期面试结论", reportMapper
                .listByUser(userId, null, null, null, null, null, 5, 0).stream()
                .filter(row -> "REPORT_READY".equals(row.getReportStatus()))
                .map(this::interviewDigest)
                .filter(text -> !text.isBlank())
                .toList());
        appendSection(material, "## 已确认的画像事实", userFactMapper.listConfirmed(userId, 10).stream()
                .map(row -> "- [" + row.getFactType() + "] " + clip(row.getTitle() + "：" + row.getContent()))
                .toList());
        List<String> existing = craftMapper.listConfirmedForPrompt(userId).stream()
                .map(CraftNoteRecord::getTitle).toList();
        if (!existing.isEmpty()) {
            material.append("## 已有套路（不要重复）\n");
            existing.forEach(title -> material.append("- ").append(clip(title)).append('\n'));
        }
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

    private CraftNoteResponse toResponse(CraftNoteRecord record) {
        return CraftNoteResponse.from(record, parseTags(record.getTagsJson()));
    }

    private CraftNoteRecord requireOwned(Long userId, Long id) {
        CraftNoteRecord record = craftMapper.findByIdAndUser(id, userId);
        if (record == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "套路不存在");
        }
        return record;
    }

    /** tags_json 反序列化；空或解析失败返回空列表，不猜测补值（同知识卡片口径）。 */
    private List<String> parseTags(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.isArray()) {
                return List.of();
            }
            List<String> tags = new ArrayList<>();
            for (JsonNode item : node) {
                String value = item.asText();
                if (value != null && !value.isBlank()) {
                    tags.add(value.length() > MAX_TAG_CHARS ? value.substring(0, MAX_TAG_CHARS) : value);
                }
            }
            return List.copyOf(tags);
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private String serializeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return "[]";
        }
        List<String> cleaned = tags.stream()
                .filter(t -> t != null && !t.isBlank())
                .map(t -> t.length() > MAX_TAG_CHARS ? t.substring(0, MAX_TAG_CHARS) : t)
                .distinct()
                .limit(MAX_TAGS)
                .toList();
        try {
            return objectMapper.writeValueAsString(cleaned);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("套路标签序列化失败", exception);
        }
    }

    private static String normalizeCategory(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase();
    }

    private static Integer clampConfidence(Integer value) {
        if (value == null) {
            return null;
        }
        return Math.max(0, Math.min(100, value));
    }

    private static String clip(String text) {
        String flat = text.replace('\n', ' ').trim();
        return flat.length() <= 200 ? flat : flat.substring(0, 200) + "…";
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private static ApiException unstructured(String reason) {
        return new ApiException(HttpStatus.BAD_GATEWAY, ErrorCode.UPSTREAM_UNAVAILABLE,
                "套路提炼失败：" + reason);
    }
}
