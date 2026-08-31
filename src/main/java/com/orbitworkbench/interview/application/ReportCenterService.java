package com.orbitworkbench.interview.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.interview.api.ReportCenterDtos.AnswerSourceItem;
import com.orbitworkbench.interview.api.ReportCenterDtos.DimensionAverage;
import com.orbitworkbench.interview.api.ReportCenterDtos.ReportDetailView;
import com.orbitworkbench.interview.api.ReportCenterDtos.ReportListItem;
import com.orbitworkbench.interview.api.ReportCenterDtos.ReportListResponse;
import com.orbitworkbench.interview.api.ReportCenterDtos.ReportSummaryResponse;
import com.orbitworkbench.interview.api.ReportCenterDtos.RuleVersionSampleView;
import com.orbitworkbench.interview.api.ReportCenterDtos.ScorePoint;
import com.orbitworkbench.interview.domain.AnswerSourceCount;
import com.orbitworkbench.interview.domain.ReportCenterRow;
import com.orbitworkbench.interview.domain.RuleVersionSample;
import com.orbitworkbench.interview.infrastructure.mapper.InterviewReportMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 报告中心的读侧聚合（14 §5、§6）。只做已存在事实的组装：
 * 列表按用户 + 会话归属过滤，趋势只在同一评分规则版本内统计且样本不足时明确不可渲染。
 */
@Service
public class ReportCenterService {

    /** 与 `00` §7.21「样本不足时不渲染虚构趋势」对应的下限（14 §6）。 */
    public static final int MIN_TREND_SAMPLES = 3;

    private static final Set<String> TOPIC_MODES = Set.of("ROTE", "PROJECT_DEEP_DIVE", "AI_TECH",
            "CODE_REVIEW", "FULL_PROCESS", "TRANSITION_TEACHING");
    private static final Set<String> FORMS = Set.of("TRAINING", "FORMAL");
    private static final Set<String> RECOMMENDATIONS = Set.of("STRONG_PASS", "PASS", "HOLD", "FAIL");
    private static final Set<Integer> DAY_WINDOWS = Set.of(7, 30, 90);
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final InterviewReportMapper reportMapper;
    private final ObjectMapper objectMapper;

    public ReportCenterService(InterviewReportMapper reportMapper, ObjectMapper objectMapper) {
        this.reportMapper = reportMapper;
        this.objectMapper = objectMapper;
    }

    public ReportListResponse list(Long userId, String days, String topicMode, String form,
                                   String recommendation, Long interviewerId, String page, String size) {
        Integer dayWindow = parseDays(days);
        String topic = optionalEnum(topicMode, TOPIC_MODES, "topicMode");
        String type = optionalEnum(form, FORMS, "form");
        String advice = optionalEnum(recommendation, RECOMMENDATIONS, "recommendation");
        int pageNumber = parsePositive(page, 1, "page");
        int pageSize = Math.min(parsePositive(size, DEFAULT_SIZE, "size"), MAX_SIZE);
        int offset = (pageNumber - 1) * pageSize;

        long total = reportMapper.countByUser(userId, dayWindow, topic, type, advice, interviewerId);
        List<ReportCenterRow> rows = total == 0
                ? List.of()
                : reportMapper.listByUser(userId, dayWindow, topic, type, advice, interviewerId,
                        pageSize, offset);
        List<ReportListItem> items = rows.stream().map(this::toItem).toList();
        return new ReportListResponse(items, total, pageNumber, pageSize);
    }

    public ReportSummaryResponse summary(Long userId, String days, String ruleVersion) {
        Integer dayWindow = parseDays(days);
        List<RuleVersionSample> samples = reportMapper.readyRuleVersionCounts(userId, dayWindow);
        List<RuleVersionSampleView> versions = samples.stream()
                .map(sample -> new RuleVersionSampleView(sample.getRuleVersion(), sample.getSampleCount()))
                .toList();
        String wanted = ruleVersion == null || ruleVersion.isBlank()
                ? (versions.isEmpty() ? null : versions.getFirst().ruleVersion())
                : ruleVersion.trim();

        List<ReportCenterRow> rows = wanted == null
                ? List.of()
                : reportMapper.summaryCandidates(userId, dayWindow, wanted);
        if (wanted != null && rows.isEmpty()) {
            // 指定了版本但该版本没有样本：不退回「全部样本」，避免把不同规则的分数混在一起。
            versions = versions.stream()
                    .anyMatch(view -> view.ruleVersion().equals(wanted))
                    ? versions
                    : appendVersion(versions, wanted);
        }

        Map<String, long[]> sums = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        List<ScorePoint> series = new ArrayList<>();
        for (ReportCenterRow row : rows) {
            if (row.getTotalScore() != null && row.getGeneratedAt() != null) {
                series.add(new ScorePoint(row.getReportId(), row.getGeneratedAt(), row.getTotalScore()));
            }
            for (Map.Entry<String, Integer> entry : parseDimensions(row.getDimensionScoresJson()).entrySet()) {
                long[] bucket = sums.computeIfAbsent(entry.getKey(), key -> new long[1]);
                bucket[0] += entry.getValue();
                counts.merge(entry.getKey(), 1, Integer::sum);
            }
        }
        List<DimensionAverage> dimensions = new ArrayList<>();
        sums.forEach((name, bucket) -> {
            int sample = counts.getOrDefault(name, 0);
            if (sample > 0) {
                dimensions.add(new DimensionAverage(name, sample,
                        (int) Math.round((double) bucket[0] / sample)));
            }
        });
        dimensions.sort(Comparator.comparing(DimensionAverage::name));
        return new ReportSummaryResponse(MIN_TREND_SAMPLES, wanted, rows.size(),
                rows.size() >= MIN_TREND_SAMPLES, List.copyOf(dimensions), List.copyOf(series), versions);
    }

    public ReportDetailView detail(Long userId, Long reportId) {
        ReportCenterRow row = reportMapper.findOwned(userId, reportId);
        if (row == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "报告不存在");
        }
        List<AnswerSourceItem> sources = reportMapper.answerSourceMix(row.getSessionId()).stream()
                .map(this::toAnswerSource)
                .toList();
        return new ReportDetailView(
                toItem(row),
                parseDimensions(row.getDimensionScoresJson()),
                parseStrings(row.getStrengthsJson()),
                parseStrings(row.getWeaknessesJson()),
                parseStrings(row.getFollowUpFindingsJson()),
                parseStrings(row.getProjectMasteryJson()),
                parseStrings(row.getKnowledgeGapsJson()),
                parseStrings(row.getStudySuggestionsJson()),
                sources,
                row.getAiModelSnapshot());
    }

    private AnswerSourceItem toAnswerSource(AnswerSourceCount count) {
        return new AnswerSourceItem(count.getAnswerSource(), count.getItemCount());
    }

    private List<RuleVersionSampleView> appendVersion(List<RuleVersionSampleView> versions, String wanted) {
        List<RuleVersionSampleView> extended = new ArrayList<>(versions);
        extended.add(new RuleVersionSampleView(wanted, 0));
        return List.copyOf(extended);
    }

    private ReportListItem toItem(ReportCenterRow row) {
        Map<String, Integer> dimensions = parseDimensions(row.getDimensionScoresJson());
        Integer average = dimensions.isEmpty()
                ? null
                : (int) Math.round(dimensions.values().stream().mapToInt(Integer::intValue).average().orElse(0));
        return new ReportListItem(
                row.getReportId(), row.getSessionId(), row.getSessionTitle(), row.getTopicMode(),
                row.getForm(), row.getRound(), row.getTargetRole(), row.getTargetExperienceBand(),
                row.getInterviewerId(), row.getInterviewerName(), row.getSessionStatus(),
                row.getReportStatus(), row.getTotalScore(), dimensions.size(), average,
                row.getHiringRecommendation(), row.getScoringRuleVersion(), row.getFailureReason(),
                row.getRetryCount() == null ? 0 : row.getRetryCount(), row.getGeneratedAt(),
                row.getScheduledAt(), row.getCreatedAt() == null ? Instant.EPOCH : row.getCreatedAt());
    }

    /**
     * 维度分是模型输出经服务端校验后写入的对象，键为中文维度名。
     * 解析失败只降级成「没有维度」，不伪造分数也不让一条脏数据打断整个列表。
     */
    private Map<String, Integer> parseDimensions(String json) {
        Map<String, Integer> parsed = readJson(json, new TypeReference<Map<String, Integer>>() {
        });
        if (parsed == null || parsed.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> cleaned = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        parsed.forEach((name, value) -> {
            if (name != null && !name.isBlank() && value != null && value >= 0 && value <= 100
                    && seen.add(name.trim())) {
                cleaned.put(name.trim(), value);
            }
        });
        return cleaned;
    }

    private List<String> parseStrings(String json) {
        List<String> parsed = readJson(json, new TypeReference<List<String>>() {
        });
        if (parsed == null || parsed.isEmpty()) {
            return List.of();
        }
        return parsed.stream().filter(item -> item != null && !item.isBlank()).map(String::trim).toList();
    }

    private <T> T readJson(String json, TypeReference<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node.isNull()) {
                return null;
            }
            return objectMapper.convertValue(node, type);
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException exception) {
            return null;
        }
    }

    private Integer parseDays(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        int value;
        try {
            value = Integer.parseInt(raw.trim());
        } catch (NumberFormatException exception) {
            throw invalid("days 必须是整数，可选 7 / 30 / 90");
        }
        if (!DAY_WINDOWS.contains(value)) {
            throw invalid("days 只允许 7 / 30 / 90");
        }
        return value;
    }

    private int parsePositive(String raw, int fallback, String field) {
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

    private String optionalEnum(String raw, Set<String> allowed, String field) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (!allowed.contains(value)) {
            throw invalid(field + " 取值不合法，可选：" + String.join("、", new TreeSet<>(allowed)));
        }
        return value;
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST, message);
    }
}
