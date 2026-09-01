package com.orbitworkbench.capability.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.capability.api.CapabilityDtos.CapabilityOverviewResponse;
import com.orbitworkbench.capability.api.CapabilityDtos.DimensionEvidenceItem;
import com.orbitworkbench.capability.api.CapabilityDtos.DimensionEvidenceResponse;
import com.orbitworkbench.capability.api.CapabilityDtos.DimensionView;
import com.orbitworkbench.capability.api.CapabilityDtos.RuleVersionSampleView;
import com.orbitworkbench.capability.api.CapabilityDtos.ScorePoint;
import com.orbitworkbench.capability.api.CapabilityDtos.SelfAssessmentView;
import com.orbitworkbench.capability.api.CapabilityDtos.SourcesView;
import com.orbitworkbench.capability.domain.CapabilityDimension;
import com.orbitworkbench.capability.domain.CapabilityReportRow;
import com.orbitworkbench.capability.domain.RuleVersionCount;
import com.orbitworkbench.capability.infrastructure.mapper.CapabilityMapper;
import com.orbitworkbench.interview.application.ReportCenterService;
import com.orbitworkbench.jobprofile.api.JobProfileDtos.JobProfileResponse;
import com.orbitworkbench.jobprofile.api.JobProfileDtos.JobProfileStateResponse;
import com.orbitworkbench.jobprofile.application.JobProfileService;
import com.orbitworkbench.practice.api.PracticeDtos.PracticeSummaryResponse;
import com.orbitworkbench.practice.application.PracticeService;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 技能图谱的读侧聚合（`16` §6–§8）。**只读**：能力值不落 `capability_record`，每次请求从
 * 已就绪报告的维度分现算（`16` §4），因此界面显示的每个数都能当场用 SQL 复验。
 *
 * <p>三层数据互不换算（D-047）：观测层的分数只来自报告维度；自评层原样透传两个序数字段；
 * 练习与项目事实只提供条数。趋势沿用报告中心的样本门槛，一个产品里不并存两套门槛。
 */
@Service
public class CapabilityService {

    /** 维度分满分。评分提示词写死 0-100（`16` §6.1），随响应下发以免界面自己猜。 */
    static final int MAX_SCORE = 100;

    private static final Set<Integer> DAY_WINDOWS = Set.of(7, 30, 90);

    private final CapabilityMapper capabilityMapper;
    private final JobProfileService jobProfileService;
    private final PracticeService practiceService;
    private final ObjectMapper objectMapper;

    public CapabilityService(CapabilityMapper capabilityMapper,
                             JobProfileService jobProfileService,
                             PracticeService practiceService,
                             ObjectMapper objectMapper) {
        this.capabilityMapper = capabilityMapper;
        this.jobProfileService = jobProfileService;
        this.practiceService = practiceService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public CapabilityOverviewResponse overview(Long userId, String days, String ruleVersion) {
        Integer dayWindow = parseDays(days);
        List<RuleVersionCount> counts = capabilityMapper.readyVersionCounts(userId, dayWindow);
        long unversioned = 0;
        List<RuleVersionSampleView> versions = new ArrayList<>();
        for (RuleVersionCount count : counts) {
            if (count.getRuleVersion() == null) {
                unversioned = count.getSampleCount();
                continue;
            }
            versions.add(new RuleVersionSampleView(count.getRuleVersion(), count.getSampleCount()));
        }
        String wanted = pickRuleVersion(ruleVersion, versions);

        List<CapabilityReportRow> rows = wanted == null
                ? List.of()
                : capabilityMapper.readyReports(userId, dayWindow, wanted);
        Map<String, long[]> sums = sumByDimension(rows);

        List<DimensionView> dimensions = new ArrayList<>();
        for (CapabilityDimension dimension : CapabilityDimension.ordered()) {
            long[] bucket = sums.get(dimension.displayName());
            int sample = bucket == null ? 0 : (int) bucket[1];
            dimensions.add(new DimensionView(dimension.displayName(), dimension.category().label(),
                    sample == 0 ? null : (int) Math.round((double) bucket[0] / sample), sample));
        }

        List<ScorePoint> series = new ArrayList<>();
        for (CapabilityReportRow row : rows) {
            if (row.getTotalScore() != null && row.getGeneratedAt() != null) {
                series.add(new ScorePoint(row.getReportId(), row.getSessionId(),
                        row.getGeneratedAt(), row.getTotalScore()));
            }
        }

        int sampleCount = rows.size();
        boolean renderable = sampleCount >= ReportCenterService.MIN_TREND_SAMPLES;
        PracticeSummaryResponse practice = practiceService.summary(userId, null);
        return new CapabilityOverviewResponse(
                dayWindow,
                wanted,
                MAX_SCORE,
                sampleCount,
                ReportCenterService.MIN_TREND_SAMPLES,
                renderable,
                renderable ? null : renderReason(sampleCount, unversioned),
                List.copyOf(dimensions),
                // 不可绘制时序列为空：界面因此没有「数据还在但曲线不该画」这种需要自己判断的中间态。
                renderable ? List.copyOf(series) : List.of(),
                selfAssessment(userId),
                new SourcesView(sampleCount, unversioned, practice.total(), practice.masteredCount(),
                        capabilityMapper.countConfirmedProjectFacts(userId)),
                List.copyOf(versions));
    }

    @Transactional(readOnly = true)
    public DimensionEvidenceResponse evidence(Long userId, String displayName, String days, String ruleVersion) {
        CapabilityDimension dimension = CapabilityDimension.findByDisplayName(displayName)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                        "未知的能力维度：" + displayName));
        Integer dayWindow = parseDays(days);
        List<RuleVersionSampleView> versions = new ArrayList<>();
        long unversioned = 0;
        for (RuleVersionCount count : capabilityMapper.readyVersionCounts(userId, dayWindow)) {
            if (count.getRuleVersion() == null) {
                unversioned = count.getSampleCount();
            } else {
                versions.add(new RuleVersionSampleView(count.getRuleVersion(), count.getSampleCount()));
            }
        }
        String wanted = pickRuleVersion(ruleVersion, versions);
        List<CapabilityReportRow> rows = wanted == null
                ? List.of()
                : capabilityMapper.readyReports(userId, dayWindow, wanted);
        List<DimensionEvidenceItem> items = new ArrayList<>();
        for (CapabilityReportRow row : rows) {
            Optional<DimensionEvidenceItem> item = evidenceItem(dimension, row);
            item.ifPresent(items::add);
        }
        return new DimensionEvidenceResponse(dimension.displayName(), dimension.category().label(),
                dayWindow, wanted, items.size(), List.copyOf(items));
    }

    /** 某份报告在该维度上有一次观测才成为证据点；缺该维度分的报告不会被算成 0 分。 */
    private Optional<DimensionEvidenceItem> evidenceItem(CapabilityDimension dimension,
                                                         CapabilityReportRow row) {
        Integer score = parseDimensions(row.getDimensionScoresJson()).get(dimension.displayName());
        if (score == null || row.getGeneratedAt() == null) {
            return Optional.empty();
        }
        return Optional.of(new DimensionEvidenceItem(row.getReportId(), row.getSessionId(),
                row.getGeneratedAt(), score));
    }

    private SelfAssessmentView selfAssessment(Long userId) {
        JobProfileStateResponse state = jobProfileService.get(userId);
        JobProfileResponse profile = state.profile();
        if (profile == null) {
            return null;
        }
        return new SelfAssessmentView(profile.javaSkillLevel(), profile.aiSkillLevel(), profile.updatedAt());
    }

    /** 每个维度累加 [分数和, 样本数]；词表外的键一律不参与（`16` §5）。 */
    private Map<String, long[]> sumByDimension(List<CapabilityReportRow> rows) {
        Map<String, long[]> sums = new LinkedHashMap<>();
        for (CapabilityReportRow row : rows) {
            parseDimensions(row.getDimensionScoresJson()).forEach((name, value) -> {
                if (CapabilityDimension.findByDisplayName(name).isPresent()) {
                    long[] bucket = sums.computeIfAbsent(name, key -> new long[2]);
                    bucket[0] += value;
                    bucket[1] += 1;
                }
            });
        }
        return sums;
    }

    /**
     * 维度分是模型输出经服务端校验后写入的对象，键为中文维度名。
     * 单份解析失败只降级成「这份报告没有维度」，不让一条脏数据打断整个图谱（与 ReportCenterService 同口径）。
     */
    private Map<String, Integer> parseDimensions(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        Map<String, Integer> parsed;
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node.isNull()) {
                return Map.of();
            }
            parsed = objectMapper.convertValue(node, new TypeReference<Map<String, Integer>>() {
            });
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException exception) {
            return Map.of();
        }
        if (parsed == null || parsed.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> cleaned = new LinkedHashMap<>();
        parsed.forEach((name, value) -> {
            if (name != null && value != null && value >= 0 && value <= MAX_SCORE) {
                cleaned.put(name.trim(), value);
            }
        });
        return cleaned;
    }

    private String renderReason(int sampleCount, long unversioned) {
        int missing = ReportCenterService.MIN_TREND_SAMPLES - sampleCount;
        StringBuilder reason = new StringBuilder("同一评分规则版本只有 ")
                .append(sampleCount).append(" 份报告，还差 ").append(missing)
                .append(" 份才能判断趋势。再完成几场面试并生成报告即可。");
        if (unversioned > 0) {
            reason.append("另有 ").append(unversioned)
                    .append(" 份历史报告未记录评分规则版本，不参与趋势。");
        }
        return reason.toString();
    }

    /** 不传版本时取样本最多的那一版；没有任何带版本报告时返回 null（即没有可比对的样本）。 */
    private String pickRuleVersion(String raw, List<RuleVersionSampleView> versions) {
        if (raw != null && !raw.isBlank()) {
            return raw.trim();
        }
        return versions.isEmpty() ? null : versions.getFirst().ruleVersion();
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

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST, message);
    }
}
