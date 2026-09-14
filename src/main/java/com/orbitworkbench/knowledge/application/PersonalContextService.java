package com.orbitworkbench.knowledge.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.craft.domain.CraftNoteRecord;
import com.orbitworkbench.craft.infrastructure.mapper.CraftNoteMapper;
import com.orbitworkbench.interview.domain.ReportCenterRow;
import com.orbitworkbench.interview.infrastructure.mapper.InterviewReportMapper;
import com.orbitworkbench.knowledge.api.KnowledgeDtos.SourceItem;
import com.orbitworkbench.learning.domain.LearningGoalRow;
import com.orbitworkbench.learning.infrastructure.mapper.LearningGoalMapper;
import com.orbitworkbench.studyplan.domain.StudyTaskRecord;
import com.orbitworkbench.studyplan.infrastructure.mapper.StudyTaskMapper;
import com.orbitworkbench.userfact.domain.UserFactRecord;
import com.orbitworkbench.userfact.infrastructure.mapper.UserFactMapper;
import com.orbitworkbench.worklog.domain.WorkLogRow;
import com.orbitworkbench.worklog.infrastructure.mapper.WorkLogMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 「我的状态」上下文装配（V48，跨数据核心问答）。
 *
 * <p>项目问答答「我的资料里怎么写」，这个范围答「我自己的情况怎么样」——把面试报告、
 * 已确认画像、复习任务、方法论、工作记录、学习目标六源聚合成带编号的资料块，
 * 由模型据此回答表现/薄弱点/待办类问题。
 *
 * <p>与全局搜索的分工：搜索按关键词找条目（找得到东西），这里按问题聚合多维事实供推理
 * （回答得了问题）。命中按关键词粗筛、无命中回退「各源最近几条」，因为「我哪个维度最弱」
 * 这类问题本就没有字面关键词可匹配。
 */
@Service
public class PersonalContextService {

    private static final int PER_SOURCE_LIMIT = 6;
    private static final int SNIPPET_MAX = 200;
    private static final int MAX_ITEM_CHARS = 1200;

    private final InterviewReportMapper reportMapper;
    private final UserFactMapper userFactMapper;
    private final StudyTaskMapper studyTaskMapper;
    private final CraftNoteMapper craftNoteMapper;
    private final WorkLogMapper workLogMapper;
    private final LearningGoalMapper learningGoalMapper;
    private final ObjectMapper objectMapper;

    public PersonalContextService(InterviewReportMapper reportMapper,
                                  UserFactMapper userFactMapper,
                                  StudyTaskMapper studyTaskMapper,
                                  CraftNoteMapper craftNoteMapper,
                                  WorkLogMapper workLogMapper,
                                  LearningGoalMapper learningGoalMapper,
                                  ObjectMapper objectMapper) {
        this.reportMapper = reportMapper;
        this.userFactMapper = userFactMapper;
        this.studyTaskMapper = studyTaskMapper;
        this.craftNoteMapper = craftNoteMapper;
        this.workLogMapper = workLogMapper;
        this.learningGoalMapper = learningGoalMapper;
        this.objectMapper = objectMapper;
    }

    /** 聚合结果：资料块（编号在前的正文）与对应来源项（同序）。 */
    public record PersonalContext(String context, List<SourceItem> sources) {
        public boolean isEmpty() {
            return sources.isEmpty();
        }
    }

    @Transactional(readOnly = true)
    public PersonalContext build(Long userId, String question) {
        String keyword = question == null ? "" : question.trim().toLowerCase(Locale.ROOT);
        List<SourceItem> sources = new ArrayList<>();
        StringBuilder context = new StringBuilder();
        int[] index = {0};

        appendReports(userId, keyword, sources, context, index);
        appendFacts(userId, keyword, sources, context, index);
        appendStudyTasks(userId, keyword, sources, context, index);
        appendCrafts(userId, keyword, sources, context, index);
        appendWorkLogs(userId, keyword, sources, context, index);
        appendGoals(userId, keyword, sources, context, index);

        return new PersonalContext(context.toString(), sources);
    }

    private void appendReports(Long userId, String keyword, List<SourceItem> sources,
                               StringBuilder context, int[] index) {
        List<ReportCenterRow> rows = reportMapper
                .listByUser(userId, null, null, null, null, null, PER_SOURCE_LIMIT, 0).stream()
                .filter(row -> "REPORT_READY".equals(row.getReportStatus()))
                .toList();
        for (ReportCenterRow row : rows) {
            StringBuilder text = new StringBuilder("面试「").append(row.getSessionTitle()).append("」")
                    .append("：总分 ").append(row.getTotalScore() == null ? "—" : row.getTotalScore())
                    .append("，建议 ").append(nullToDash(row.getHiringRecommendation()));
            if (row.getEndedAt() != null) {
                text.append("，结束于 ").append(row.getEndedAt().toString(), 0, 10);
            }
            String dims = dimensionSummary(row.getDimensionScoresJson());
            if (!dims.isBlank()) {
                text.append("。维度得分：").append(dims);
            }
            appendItems(text, "薄弱项", row.getWeaknessesJson(), index);
            appendItems(text, "知识缺口", row.getKnowledgeGapsJson(), index);
            if (!matched(keyword, text.toString())) {
                continue;
            }
            add(sources, context, index[0], "面试报告", text.toString());
        }
    }

    private void appendFacts(Long userId, String keyword, List<SourceItem> sources,
                             StringBuilder context, int[] index) {
        List<UserFactRecord> facts = userFactMapper.listConfirmed(userId, PER_SOURCE_LIMIT * 2);
        List<String> lines = new ArrayList<>();
        List<String> hitTitles = new ArrayList<>();
        for (UserFactRecord fact : facts) {
            String line = "[" + fact.getFactType() + "] " + fact.getTitle() + "：" + fact.getContent();
            if (keyword.isEmpty() || matched(keyword, line) || lines.size() < 4) {
                lines.add(line);
                hitTitles.add(fact.getTitle());
            }
        }
        if (lines.isEmpty()) {
            return;
        }
        String text = "已确认画像：" + String.join("；", lines);
        add(sources, context, index[0], "画像事实", text);
    }

    private void appendStudyTasks(Long userId, String keyword, List<SourceItem> sources,
                                  StringBuilder context, int[] index) {
        List<StudyTaskRecord> tasks = studyTaskMapper.listByUser(userId, null).stream()
                .filter(task -> task.getStatus() != null && !"COMPLETED".equals(task.getStatus().name()))
                .limit(PER_SOURCE_LIMIT)
                .toList();
        if (tasks.isEmpty()) {
            return;
        }
        List<String> lines = new ArrayList<>();
        for (StudyTaskRecord task : tasks) {
            lines.add(task.getTitle()
                    + (task.getDueDate() == null ? "" : "（截止 " + task.getDueDate() + "）")
                    + " [" + task.getStatus() + "]");
        }
        String text = "待办复习任务（" + tasks.size() + " 条）：" + String.join("；", lines);
        if (!keyword.isEmpty() && !matched(keyword, text) && !pendingKeyword(keyword)) {
            // 关键词与任务无关时不硬塞，避免污染无关问题的上下文
            return;
        }
        add(sources, context, index[0], "复习任务", text);
    }

    private void appendCrafts(Long userId, String keyword, List<SourceItem> sources,
                              StringBuilder context, int[] index) {
        List<CraftNoteRecord> crafts = craftNoteMapper.listConfirmedForPrompt(userId).stream()
                .limit(PER_SOURCE_LIMIT)
                .toList();
        if (crafts.isEmpty()) {
            return;
        }
        List<String> lines = new ArrayList<>();
        for (CraftNoteRecord craft : crafts) {
            lines.add("[" + craft.getCategory() + "] " + craft.getTitle()
                    + "（用于：" + craft.getWhenToUse() + "）");
        }
        String text = "已沉淀方法论：" + String.join("；", lines);
        if (!matched(keyword, text)) {
            return;
        }
        add(sources, context, index[0], "方法论", text);
    }

    private void appendWorkLogs(Long userId, String keyword, List<SourceItem> sources,
                                StringBuilder context, int[] index) {
        List<WorkLogRow> logs = workLogMapper.listByUser(userId, PER_SOURCE_LIMIT, 0);
        if (logs.isEmpty()) {
            return;
        }
        List<String> lines = new ArrayList<>();
        for (WorkLogRow log : logs) {
            lines.add("[" + log.getCategory() + "] " + clip(log.getTitle() + "：" + log.getContent()));
        }
        String text = "近期工作记录：" + String.join("；", lines);
        if (!matched(keyword, text)) {
            return;
        }
        add(sources, context, index[0], "工作记录", text);
    }

    private void appendGoals(Long userId, String keyword, List<SourceItem> sources,
                             StringBuilder context, int[] index) {
        List<LearningGoalRow> goals = learningGoalMapper.listByUser(userId, PER_SOURCE_LIMIT, 0);
        if (goals.isEmpty()) {
            return;
        }
        List<String> lines = new ArrayList<>();
        for (LearningGoalRow goal : goals) {
            lines.add(goal.getTitle() + " [" + goal.getStatus() + "]");
        }
        String text = "学习目标：" + String.join("；", lines);
        if (!matched(keyword, text)) {
            return;
        }
        add(sources, context, index[0], "学习目标", text);
    }

    private void add(List<SourceItem> sources, StringBuilder context, int number,
                     String category, String text) {
        int n = sources.size() + 1;
        sources.add(new SourceItem(category, n, snippet(text, SNIPPET_MAX), category));
        context.append('[').append(n).append("] ").append(category).append('\n')
                .append(truncate(text, MAX_ITEM_CHARS)).append("\n\n");
    }

    /** 维度得分按「低分优先」展示：问薄弱点时低分维度最该被模型先看到。 */
    private String dimensionSummary(String json) {
        if (json == null || json.isBlank()) {
            return "";
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.isObject()) {
                return "";
            }
            java.util.Map<String, Integer> scores = new java.util.LinkedHashMap<>();
            node.fields().forEachRemaining(entry -> scores.put(entry.getKey(), entry.getValue().asInt()));
            return scores.entrySet().stream()
                    .sorted(java.util.Map.Entry.comparingByValue())
                    .map(e -> e.getKey() + " " + e.getValue())
                    .collect(java.util.stream.Collectors.joining("、"));
        } catch (Exception exception) {
            return "";
        }
    }

    private void appendItems(StringBuilder text, String label, String json, int[] index) {
        if (json == null || json.isBlank()) {
            return;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.isArray() || node.isEmpty()) {
                return;
            }
            List<String> items = new ArrayList<>();
            for (JsonNode item : node) {
                String value = item.asText("").trim();
                if (!value.isEmpty()) {
                    items.add(value.length() > 60 ? value.substring(0, 60) + "…" : value);
                }
                if (items.size() >= 4) {
                    break;
                }
            }
            if (!items.isEmpty()) {
                text.append("。").append(label).append("：").append(String.join("；", items));
            }
        } catch (Exception ignored) {
            // 报告 JSON 脏数据只是少一段上下文，不抛
        }
    }

    private static boolean matched(String keyword, String text) {
        if (keyword.isEmpty()) {
            return true;
        }
        String comparable = keyword.toLowerCase(Locale.ROOT);
        String haystack = text.toLowerCase(Locale.ROOT);
        if (haystack.contains(comparable)) {
            return true;
        }
        // 中文无空格，2 字连续命中即算相关
        for (int i = 0; i + 2 <= comparable.length(); i++) {
            String gram = comparable.substring(i, i + 2);
            if (isCjk(gram) && haystack.contains(gram)) {
                return true;
            }
        }
        // 拉丁词要求整词长度 >= 3：否则 "redis" 的 "ed" 会误命中 "planned"
        for (String token : comparable.split("[^\\p{Alnum}]+")) {
            if (token.length() >= 3 && haystack.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCjk(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.UnicodeScript.of(value.charAt(i)) == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }

    private static boolean pendingKeyword(String keyword) {
        return keyword.contains("待办") || keyword.contains("任务") || keyword.contains("截止")
                || keyword.contains("复习") || keyword.contains("安排");
    }

    private static String clip(String text) {
        String flat = text.replace('\n', ' ').trim();
        return flat.length() <= 160 ? flat : flat.substring(0, 160) + "…";
    }

    private static String snippet(String text, int max) {
        String flat = text.replace('\n', ' ').trim();
        return flat.length() <= max ? flat : flat.substring(0, max) + "…";
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}
