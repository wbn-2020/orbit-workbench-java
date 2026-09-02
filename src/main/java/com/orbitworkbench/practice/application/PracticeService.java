package com.orbitworkbench.practice.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.interview.api.InterviewDtos.ReportResponse;
import com.orbitworkbench.interview.api.InterviewDtos.SessionDetailResponse;
import com.orbitworkbench.interview.api.InterviewDtos.TurnResponse;
import com.orbitworkbench.interview.application.InterviewReportService;
import com.orbitworkbench.interview.application.InterviewSessionService;
import com.orbitworkbench.practice.api.PracticeDtos.AttemptRequest;
import com.orbitworkbench.practice.api.PracticeDtos.AttemptResponse;
import com.orbitworkbench.practice.api.PracticeDtos.ClassificationRequest;
import com.orbitworkbench.practice.api.PracticeDtos.CreateItemRequest;
import com.orbitworkbench.practice.api.PracticeDtos.ImportResponse;
import com.orbitworkbench.practice.api.PracticeDtos.ItemDetailResponse;
import com.orbitworkbench.practice.api.PracticeDtos.ItemListResponse;
import com.orbitworkbench.practice.api.PracticeDtos.ItemResponse;
import com.orbitworkbench.practice.api.PracticeDtos.PracticeSummaryResponse;
import com.orbitworkbench.practice.api.PracticeDtos.TopicCount;
import com.orbitworkbench.practice.domain.MasteryStatus;
import com.orbitworkbench.practice.domain.PracticeAttemptRecord;
import com.orbitworkbench.practice.domain.PracticeItemRecord;
import com.orbitworkbench.practice.domain.PracticeItemRow;
import com.orbitworkbench.practice.domain.PracticeResult;
import com.orbitworkbench.practice.domain.PracticeSource;
import com.orbitworkbench.practice.domain.ReviewDateSource;
import com.orbitworkbench.practice.infrastructure.mapper.PracticeAttemptMapper;
import com.orbitworkbench.practice.infrastructure.mapper.PracticeItemMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 错题本与专项复练（C-03，口径见 `15` §5–§7）。
 *
 * <p>两条贯穿全类的约束：条目只能来自已经发生的观测（报告清单、真实轮次、用户手工），
 * 以及尝试只新增、掌握状态永远是尝试历史的纯函数。
 */
@Service
public class PracticeService {

    /** 需要连续通过这么多次才算已掌握（`15` §6）。 */
    public static final int MASTERED_STREAK = 2;
    /** 计入已掌握的每次通过所要求的最低自评。 */
    public static final int MASTERED_SELF_SCORE = 80;
    public static final int MAX_QUESTION_CHARS = 4000;
    /**
     * 复习日阶梯（`15` §11 第二条，C-03e）：末尾连续「答通」第 n 次即取第 n 档，超出档位停在最后一档。
     * 没答通（{@code RETRY} / {@code PARTIAL}）回落到第 1 档，与 §6 里「一次 RETRY 或 PARTIAL 即中断」同一口径。
     */
    static final List<Integer> REVIEW_LADDER_DAYS = List.of(1, 3, 7, 14);

    /** 报告四类清单 → 条目归类名。这些名字就是报告里那一段的名字，不额外发明维度。 */
    private static final Map<String, String> REPORT_CATEGORIES = new LinkedHashMap<>();
    /** 轮次作答行为 → 归类名。按可观测事实归类，不是评分维度（`15` §5.2）。 */
    private static final Map<String, String> TURN_TOPICS = Map.of(
            "PROMPTED", "提示后作答",
            "AI_ASSISTED", "AI 辅助作答",
            "AI_GENERATED", "AI 生成作答");
    private static final String UNANSWERED_TOPIC = "未作答";
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    static {
        REPORT_CATEGORIES.put("weaknesses", "薄弱点");
        REPORT_CATEGORIES.put("followUpFindings", "追问暴露的问题");
        REPORT_CATEGORIES.put("projectMastery", "项目掌握薄弱点");
        REPORT_CATEGORIES.put("knowledgeGaps", "技术知识盲区");
    }

    private final PracticeItemMapper itemMapper;
    private final PracticeAttemptMapper attemptMapper;
    private final InterviewReportService reportService;
    private final InterviewSessionService sessionService;
    private final ObjectMapper objectMapper;

    public PracticeService(PracticeItemMapper itemMapper,
                           PracticeAttemptMapper attemptMapper,
                           InterviewReportService reportService,
                           InterviewSessionService sessionService,
                           ObjectMapper objectMapper) {
        this.itemMapper = itemMapper;
        this.attemptMapper = attemptMapper;
        this.reportService = reportService;
        this.sessionService = sessionService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ItemListResponse list(Long userId, String mastery, String sourceType, String topic,
                                 String archived, String page, String size) {
        MasteryStatus masteryValue = optionalEnum(mastery, MasteryStatus.class, "mastery");
        PracticeSource sourceValue = optionalEnum(sourceType, PracticeSource.class, "sourceType");
        Boolean archivedValue = parseArchived(archived);
        int pageNumber = parsePositive(page, 1, "page");
        int pageSize = Math.min(parsePositive(size, DEFAULT_SIZE, "size"), MAX_SIZE);
        String trimmedTopic = topic == null || topic.isBlank() ? null : topic.trim();
        long total = itemMapper.countByUser(userId, masteryValue, sourceValue, trimmedTopic, archivedValue);
        List<ItemResponse> items = total == 0
                ? List.of()
                : itemMapper.listByUser(userId, masteryValue, sourceValue, trimmedTopic, archivedValue,
                        pageSize, (pageNumber - 1) * pageSize)
                .stream()
                .map(row -> ItemResponse.from(row, consecutivePassed(row.getItemId())))
                .toList();
        return new ItemListResponse(items, total, pageNumber, pageSize);
    }

    @Transactional(readOnly = true)
    public PracticeSummaryResponse summary(Long userId, String archived) {
        Boolean archivedValue = parseArchived(archived);
        long total = itemMapper.countByUser(userId, null, null, null, archivedValue);
        long newCount = itemMapper.countByUser(userId, MasteryStatus.NEW, null, null, archivedValue);
        long learning = itemMapper.countByUser(userId, MasteryStatus.LEARNING, null, null, archivedValue);
        long mastered = itemMapper.countByUser(userId, MasteryStatus.MASTERED, null, null, archivedValue);
        List<TopicCount> topics = new ArrayList<>();
        for (String topic : itemMapper.listTopics(userId, archivedValue)) {
            long itemCount = itemMapper.countByUser(userId, null, null, topic, archivedValue);
            long notMastered = itemCount
                    - itemMapper.countByUser(userId, MasteryStatus.MASTERED, null, topic, archivedValue);
            topics.add(new TopicCount(topic, itemCount, notMastered));
        }
        Instant lastAttemptAt = attemptMapper.lastAttemptAtByUser(userId, archivedValue);
        return new PracticeSummaryResponse(total, newCount, learning, mastered, total > 0,
                List.copyOf(topics), lastAttemptAt, MASTERED_STREAK, MASTERED_SELF_SCORE,
                REVIEW_LADDER_DAYS);
    }

    @Transactional(readOnly = true)
    public ItemDetailResponse detail(Long userId, Long itemId) {
        PracticeItemRow row = requireOwned(userId, itemId);
        List<PracticeAttemptRecord> attempts = attemptMapper.listByItem(itemId);
        return new ItemDetailResponse(ItemResponse.from(row, streakOf(attempts)),
                attempts.stream().map(AttemptResponse::from).toList());
    }

    @Transactional
    public ItemResponse create(Long userId, CreateItemRequest request) {
        String topic = requireText(request.topic(), 128, "topic");
        String question = requireText(request.question(), MAX_QUESTION_CHARS, "question");
        String reference = trimToNull(request.referenceAnswer());
        if (reference != null && reference.length() > MAX_QUESTION_CHARS) {
            throw invalid("参考答案长度超限");
        }
        Instant now = Instant.now();
        PracticeItemRecord record = newItem(userId, PracticeSource.MANUAL, null, topic, question,
                reference, now);
        itemMapper.insert(record);
        return ItemResponse.from(requireOwned(userId, record.getId()), 0);
    }

    /** 把某场会话报告里的四类薄弱清单转成条目；重复导入跳过已有条目（`15` §5.1）。 */
    @Transactional
    public ImportResponse importFromReport(Long userId, Long sessionId) {
        ReportResponse report = reportService.getReportState(userId, sessionId).report();
        if (report == null) {
            throw conflict("该会话还没有报告记录，没有可导入的薄弱点。");
        }
        if (!"REPORT_READY".equals(report.status())) {
            throw conflict("该会话的报告尚未生成完成，当前状态无法导入薄弱点。");
        }
        SessionDetailResponse detail = sessionService.get(userId, sessionId);
        Map<String, String> lists = new LinkedHashMap<>();
        lists.put("weaknesses", report.weaknessesJson());
        lists.put("followUpFindings", report.followUpFindingsJson());
        lists.put("projectMastery", report.projectMasteryJson());
        lists.put("knowledgeGaps", report.knowledgeGapsJson());

        int created = 0;
        int skipped = 0;
        Set<String> topics = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : lists.entrySet()) {
            String topic = REPORT_CATEGORIES.get(entry.getKey());
            for (String text : readStrings(entry.getValue())) {
                if (itemMapper.countSameSourceText(userId, PracticeSource.REPORT, report.id(), text) > 0) {
                    skipped++;
                    continue;
                }
                Instant now = Instant.now();
                itemMapper.insert(newItem(userId, PracticeSource.REPORT, report.id(), topic, text, null, now));
                topics.add(topic);
                created++;
            }
        }
        String note = created == 0
                ? (skipped == 0 ? "这份报告没有记录任何薄弱点清单内容，因此没有新增条目。"
                        : "报告的薄弱点已全部在错题本里，本次没有重复入队。")
                : "已按报告的薄弱点、追问暴露的问题、项目掌握与知识盲区入队；这些条目只能追溯到报告，"
                        + "报告未记录它们对应的问题与维度。";
        return new ImportResponse(sessionId, detail.session().title(), report.id(), created, skipped,
                List.copyOf(topics), note);
    }

    /** 把某场会话里没有独立答上来的轮次转成条目（`15` §5.2）。 */
    @Transactional
    public ImportResponse importFromSession(Long userId, Long sessionId) {
        SessionDetailResponse detail = sessionService.get(userId, sessionId);
        int created = 0;
        int skipped = 0;
        Set<String> topics = new LinkedHashSet<>();
        for (TurnResponse turn : detail.turns()) {
            String topic = turnTopic(turn);
            if (topic == null) {
                continue;
            }
            if (itemMapper.countSameSourceText(userId, PracticeSource.INTERVIEW_TURN, turn.id(),
                    turn.question()) > 0) {
                skipped++;
                continue;
            }
            Instant now = Instant.now();
            itemMapper.insert(newItem(userId, PracticeSource.INTERVIEW_TURN, turn.id(), topic,
                    turn.question(), null, now));
            topics.add(topic);
            created++;
        }
        String note = created == 0
                ? (skipped == 0 ? "这场面试的每一题都是独立作答完成的，没有需要入队的轮次。"
                        : "未独立作答的轮次已经都在错题本里，本次没有重复入队。")
                : "按作答行为入队：未作答、提示后作答与 AI 代答都属于可观测事实，不是能力评分。";
        return new ImportResponse(sessionId, detail.session().title(), null, created, skipped,
                List.copyOf(topics), note);
    }

    /**
     * 提交一次重练：只新增尝试，再按尝试历史重算并落回掌握状态，同时按阶梯推进复习日（`15` §11）。
     *
     * <p>推进复习日是这一步的既定副作用：当前已经用来源列区分「用户手设」与「规则算出」，
     * 但尚未支持钉住日期，因此此前手工设定的复习日仍会在这里被规则覆盖，
     * 界面对这一条必须明说（`15` §11 代价列）。
     */
    @Transactional
    public ItemDetailResponse addAttempt(Long userId, Long itemId, AttemptRequest request) {
        requireOwned(userId, itemId);
        String answer = requireText(request.answer(), 8000, "answer");
        PracticeResult result = parseEnum(PracticeResult.class, request.result(), "result");
        if (result == PracticeResult.PASSED && request.selfScore() == null) {
            throw invalid("选择「答对了」时必须给出 0-100 的自评，否则无法判断是否达到已掌握标准");
        }
        PracticeAttemptRecord attempt = new PracticeAttemptRecord();
        attempt.setPracticeItemId(itemId);
        attempt.setAnswer(answer);
        attempt.setSelfScore(request.selfScore());
        attempt.setResult(result);
        attempt.setFeedback(trimToNull(request.feedback()));
        attempt.setAttemptedAt(Instant.now());
        attemptMapper.insert(attempt);

        List<PracticeAttemptRecord> attempts = attemptMapper.listByItem(itemId);
        MasteryStatus status = recompute(attempts);
        LocalDate nextReviewDate = scheduleReview(attempts);
        Instant now = Instant.now();
        itemMapper.updateAfterAttempt(itemId, userId, status, nextReviewDate,
                ReviewDateSource.RULE, now);
        PracticeItemRow row = requireOwned(userId, itemId);
        return new ItemDetailResponse(ItemResponse.from(row, streakOf(attempts)),
                attempts.stream().map(AttemptResponse::from).toList());
    }

    @Transactional
    public ItemResponse classify(Long userId, Long itemId, ClassificationRequest request) {
        PracticeItemRow current = requireOwned(userId, itemId);
        String topic = requireText(request.topic(), 128, "topic");
        String reference = trimToNull(request.referenceAnswer());
        if (reference != null && reference.length() > MAX_QUESTION_CHARS) {
            throw invalid("参考答案长度超限");
        }
        Instant expected = parseInstant(request.expectedUpdatedAt());
        Instant now = Instant.now();
        int updated = itemMapper.updateClassification(itemId, userId, topic, reference,
                request.nextReviewDate(),
                request.nextReviewDate() == null ? null : ReviewDateSource.MANUAL,
                expected, now);
        if (updated != 1) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "条目已被其他操作更新，请重新加载后再修改（当前归类：" + current.getTopic() + "）");
        }
        return ItemResponse.from(requireOwned(userId, itemId), consecutivePassed(itemId));
    }

    @Transactional
    public ItemResponse setArchived(Long userId, Long itemId, boolean archived) {
        requireOwned(userId, itemId);
        int updated = itemMapper.updateArchived(itemId, userId, archived, Instant.now());
        if (updated != 1) {
            throw conflict("条目状态已变化，请重新加载");
        }
        return ItemResponse.from(requireOwned(userId, itemId), consecutivePassed(itemId));
    }

    private PracticeItemRow requireOwned(Long userId, Long itemId) {
        PracticeItemRow row = itemMapper.findOwned(userId, itemId);
        if (row == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "练习条目不存在");
        }
        return row;
    }

    private PracticeItemRecord newItem(Long userId, PracticeSource source, Long sourceId, String topic,
                                       String question, String reference, Instant now) {
        PracticeItemRecord record = new PracticeItemRecord();
        record.setUserId(userId);
        record.setSourceType(source);
        record.setSourceId(sourceId);
        record.setTopic(topic);
        record.setQuestion(question);
        record.setReferenceAnswer(reference);
        record.setMasteryStatus(MasteryStatus.NEW);
        record.setReviewDateSource(null);
        record.setArchived(false);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        return record;
    }

    /** 返回该轮次的入队归类名；不需要入队时返回 null。 */
    private static String turnTopic(TurnResponse turn) {
        if (turn.answer() == null || turn.answer().isBlank()) {
            return UNANSWERED_TOPIC;
        }
        return TURN_TOPICS.get(turn.answerSource() == null ? "" : turn.answerSource());
    }

    private int consecutivePassed(Long itemId) {
        return streakOf(attemptMapper.listByItem(itemId));
    }

    /** 复习日只由这一次尝试之后的尝试历史决定，与条目上原有的日期无关，因此同一份历史随时可重算出同一结果。 */
    static LocalDate scheduleReview(List<PracticeAttemptRecord> attempts) {
        return LocalDate.now().plusDays(ladderDays(streakOf(attempts)));
    }

    /** 连续答通第 n 次取第 n 档；没答通（n=0）回落到第 1 档。 */
    static int ladderDays(int streak) {
        int index = streak <= 0 ? 0 : Math.min(streak, REVIEW_LADDER_DAYS.size()) - 1;
        return REVIEW_LADDER_DAYS.get(index);
    }

    private static int streakOf(List<PracticeAttemptRecord> attempts) {
        int streak = 0;
        for (int index = attempts.size() - 1; index >= 0; index--) {
            if (attempts.get(index).getResult() != PracticeResult.PASSED) {
                break;
            }
            streak++;
        }
        return streak;
    }

    /**
     * 掌握状态是尝试历史的纯函数（`15` §6）：末尾连续 PASSED 足够多且这些通过都达到自评线才算已掌握，
     * 因此重练失败可以诚实地把状态退回 LEARNING。
     */
    static MasteryStatus recompute(List<PracticeAttemptRecord> attempts) {
        if (attempts.isEmpty()) {
            return MasteryStatus.NEW;
        }
        int streak = 0;
        boolean allAboveThreshold = true;
        for (int index = attempts.size() - 1; index >= 0; index--) {
            PracticeAttemptRecord attempt = attempts.get(index);
            if (attempt.getResult() != PracticeResult.PASSED) {
                break;
            }
            streak++;
            if (attempt.getSelfScore() == null || attempt.getSelfScore() < MASTERED_SELF_SCORE) {
                allAboveThreshold = false;
            }
        }
        return streak >= MASTERED_STREAK && allAboveThreshold ? MasteryStatus.MASTERED : MasteryStatus.LEARNING;
    }

    private List<String> readStrings(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.isArray()) {
                return List.of();
            }
            List<String> values = objectMapper.convertValue(node, new TypeReference<List<String>>() {
            });
            if (values == null) {
                return List.of();
            }
            return values.stream().filter(value -> value != null && !value.isBlank()).map(String::trim)
                    .map(value -> value.length() > MAX_QUESTION_CHARS
                            ? value.substring(0, MAX_QUESTION_CHARS) : value)
                    .toList();
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            return List.of();
        }
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String raw, String field) {
        for (E candidate : type.getEnumConstants()) {
            if (candidate.name().equals(raw == null ? "" : raw.trim())) {
                return candidate;
            }
        }
        throw invalid(field + " 取值不合法，可选：" + String.join("、",
                Arrays.stream(type.getEnumConstants()).map(Enum::name).toList()));
    }

    private static <E extends Enum<E>> E optionalEnum(String raw, Class<E> type, String field) {
        return raw == null || raw.isBlank() ? null : parseEnum(type, raw, field);
    }

    /** 归档语义（`15` §6）：归档就是移出队列，因此缺省只看未归档；只有显式 all 才把两类一起返回。 */
    private static Boolean parseArchived(String raw) {
        if (raw == null || raw.isBlank() || "false".equalsIgnoreCase(raw.trim())) {
            return Boolean.FALSE;
        }
        if ("true".equalsIgnoreCase(raw.trim())) {
            return Boolean.TRUE;
        }
        if ("all".equalsIgnoreCase(raw.trim())) {
            return null;
        }
        throw invalid("archived 只接受 true / false / all");
    }

    private static int parsePositive(String raw, int fallback, String field) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        int value;
        try {
            value = Integer.parseInt(raw.trim());
        } catch (NumberFormatException exception) {
            throw invalid(field + " 必须是整数");
        }
        if (value < 1) {
            throw invalid(field + " 必须大于 0");
        }
        return value;
    }

    private static Instant parseInstant(String raw) {
        try {
            return Instant.parse(raw.trim());
        } catch (DateTimeParseException exception) {
            throw invalid("expectedUpdatedAt 格式不正确，请使用接口返回的 updated_at 原值");
        }
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

    private static String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        return value.isEmpty() ? null : value;
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST, message);
    }

    private static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT, message);
    }
}
