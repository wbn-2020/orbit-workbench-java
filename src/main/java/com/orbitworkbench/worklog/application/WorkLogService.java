package com.orbitworkbench.worklog.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.aiconnection.application.AiScenarioExecutionService;
import com.orbitworkbench.aiconnection.domain.AiScenario;
import com.orbitworkbench.ai.application.AiOutputCleaner;
import com.orbitworkbench.ai.application.PromptCatalog;
import com.orbitworkbench.ai.application.RequestRejectedException;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.worklog.api.KnowledgeCardDtos.KnowledgeCardResponse;
import com.orbitworkbench.worklog.api.WorkLogDtos.CreateWorkLogRequest;
import com.orbitworkbench.worklog.api.WorkLogDtos.WorkLogResponse;
import com.orbitworkbench.worklog.infrastructure.mapper.KnowledgeCardMapper;
import com.orbitworkbench.worklog.domain.KnowledgeCardRecord;
import com.orbitworkbench.worklog.domain.KnowledgeCardRow;
import com.orbitworkbench.worklog.domain.WorkLogCategory;
import com.orbitworkbench.worklog.infrastructure.mapper.WorkLogMapper;
import com.orbitworkbench.worklog.domain.WorkLogRecord;
import com.orbitworkbench.worklog.domain.WorkLogRow;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 工作记录与知识蒸馏（v2 工作沉淀域）。
 *
 * <p>蒸馏刻意把 AI 调用放在数据库事务之外：先读取自有记录，再调 AI 提炼要点，
 * 最后在独立事务里落库知识卡片并标记原记录已蒸馏，避免长耗时外部调用占用连接。
 */
@Service
public class WorkLogService {

    private static final int LIST_LIMIT = 500;
    private static final int DISTILL_MAX_TOKENS = 800;
    /** 卡片摘要落库上限：summary 列为 TEXT，此处限 2000 字防模型跑飞。 */
    private static final int CARD_SUMMARY_MAX = 2000;
    private static final Duration DISTILL_TIMEOUT = Duration.ofSeconds(60);
    /** 正文见 resources/prompts/worklog-distill-system.txt。 */
    private static final String DISTILL_SYSTEM_PROMPT = PromptCatalog.load("worklog-distill-system");

    private static final Map<String, WorkLogCategory> CATEGORY_ALIASES = Map.of(
            "project", WorkLogCategory.PROJECT,
            "incident", WorkLogCategory.INCIDENT,
            "decision", WorkLogCategory.DECISION,
            "learning", WorkLogCategory.LEARNING,
            "other", WorkLogCategory.OTHER);

    private final WorkLogMapper workLogMapper;
    private final KnowledgeCardMapper knowledgeCardMapper;
    private final AiScenarioExecutionService executionService;
    private final ObjectMapper objectMapper;
    private final WorkLogDistillationWriteService writeService;

    public WorkLogService(WorkLogMapper workLogMapper, KnowledgeCardMapper knowledgeCardMapper,
                          AiScenarioExecutionService executionService, ObjectMapper objectMapper,
                          WorkLogDistillationWriteService writeService) {
        this.workLogMapper = workLogMapper;
        this.knowledgeCardMapper = knowledgeCardMapper;
        this.executionService = executionService;
        this.objectMapper = objectMapper;
        this.writeService = writeService;
    }

    @Transactional(readOnly = true)
    public List<WorkLogResponse> list(Long userId) {
        return workLogMapper.listByUser(userId, LIST_LIMIT, 0).stream()
                .map(WorkLogResponse::from)
                .toList();
    }

    @Transactional
    public WorkLogResponse create(Long userId, CreateWorkLogRequest request) {
        WorkLogCategory category = parseCategory(request.category());
        String title = requireText(request.title(), 255, "title");
        String content = requireText(request.content(), 20000, "content");
        Instant now = Instant.now();
        WorkLogRecord record = new WorkLogRecord();
        record.setUserId(userId);
        record.setTitle(title);
        record.setContent(content);
        record.setCategory(category);
        record.setDistilled(false);
        record.setVersion(1);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        workLogMapper.insert(record);
        WorkLogRow row = requireOwned(userId, record.getId());
        return WorkLogResponse.from(row);
    }

    /**
     * 蒸馏：读取自有记录 → 调 AI 提炼 → 独立事务落库卡片并标记已蒸馏。
     * AI 调用在事务之外，不占用数据库连接。
     */
    public KnowledgeCardResponse distill(Long userId, Long id) {
        WorkLogRow log = requireOwned(userId, id);
        KnowledgeCardRow existing = knowledgeCardMapper.findBySourceLog(userId, id);
        if (existing != null) {
            return KnowledgeCardResponse.from(existing,
                    KnowledgeCardService.parseTags(objectMapper, existing.getTagsJson()));
        }
        String userPrompt = "工作记录标题：" + log.getTitle() + "\n工作内容：" + log.getContent();
        String aiText;
        try {
            aiText = executionService.executeText(AiScenario.PROJECT_FACT, userId, null,
                    DISTILL_SYSTEM_PROMPT, userPrompt, DISTILL_MAX_TOKENS, DISTILL_TIMEOUT);
        } catch (RequestRejectedException exception) {
            // 请求根本没发出去（如场景绑定的连接不支持所需能力）：与报告链路同口径，
            // 把 4xx 语义原样透传，不谎报成「上游失败」。
            throw exception;
        } catch (ApiException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, ErrorCode.UPSTREAM_UNAVAILABLE,
                    "知识蒸馏调用 AI 失败：" + exception.getMessage());
        }
        KnowledgeCardContent content = parseAiCard(aiText, log.getCategory());
        writeService.persist(userId, id, log.getTitle(),
                content.summary(), content.tags());
        // The unique key and upsert make this read return the winner when requests race.
        KnowledgeCardRow row = knowledgeCardMapper.findBySourceLog(userId, id);
        if (row == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.UNKNOWN_PROVIDER_ERROR,
                    "知识卡片写入后读取失败");
        }
        return KnowledgeCardResponse.from(row, KnowledgeCardService.parseTags(objectMapper, row.getTagsJson()));
    }

    private WorkLogRow requireOwned(Long userId, Long id) {
        WorkLogRow row = workLogMapper.findOwned(userId, id);
        if (row == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "工作记录不存在");
        }
        return row;
    }

    /**
     * 解析 AI 返回的卡片：优先解析 JSON 的 summary/tags；解析失败就把整段文本当摘要、
     * 标签退化为来源分类，保证蒸馏在任何情况下都能产出可用卡片。
     * 解析复用 B-07 的 AiOutputCleaner：围栏剥离与括号配平提取已过真实链路验收，
     * 能吃下「围栏前缀散文 + ```json 围栏 + 尾部说明」这类脏输出。
     */
    private KnowledgeCardContent parseAiCard(String raw, WorkLogCategory fallbackCategory) {
        String json = AiOutputCleaner.extractJsonObject(raw);
        if (json != null) {
            try {
                JsonNode node = objectMapper.readTree(json);
                if (node.isObject()) {
                    String summary = node.path("summary").asText(null);
                    List<String> tags = new ArrayList<>();
                    JsonNode tagsNode = node.path("tags");
                    if (tagsNode.isArray()) {
                        for (JsonNode tag : tagsNode) {
                            String value = tag.asText();
                            if (value != null && !value.isBlank()) {
                                tags.add(value.trim());
                            }
                        }
                    }
                    if (summary == null || summary.isBlank()) {
                        summary = node.toString();
                    }
                    if (tags.isEmpty()) {
                        tags.add(fallbackCategory.name());
                    }
                    return new KnowledgeCardContent(AiOutputCleaner.summarize(summary, CARD_SUMMARY_MAX), tags);
                }
            } catch (JsonProcessingException ignored) {
                // 退化成把整段文本当作摘要
            }
        }
        String fallback = raw == null ? "" : AiOutputCleaner.stripCodeFence(raw).trim();
        return new KnowledgeCardContent(AiOutputCleaner.summarize(fallback, CARD_SUMMARY_MAX),
                List.of(fallbackCategory.name()));
    }

    private static WorkLogCategory parseCategory(String raw) {
        if (raw == null || raw.isBlank()) {
            throw invalid("category 不能为空");
        }
        WorkLogCategory category = CATEGORY_ALIASES.get(raw.trim().toLowerCase());
        if (category == null) {
            throw invalid("category 取值不合法，可选：project、incident、decision、learning、other");
        }
        return category;
    }

    private static String requireText(String raw, int maxChars, String field) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            throw invalid(field + " 不能为空");
        }
        if (value.length() > maxChars) {
            throw invalid(field + " 长度不得超过 " + maxChars + " 字");
        }
        return value;
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST, message);
    }

    private record KnowledgeCardContent(String summary, List<String> tags) {
    }
}
