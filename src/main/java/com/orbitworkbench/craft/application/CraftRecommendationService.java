package com.orbitworkbench.craft.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.craft.api.CraftDtos.CraftRecommendationsResponse;
import com.orbitworkbench.craft.api.CraftDtos.WeaknessCraftRecommendation;
import com.orbitworkbench.craft.domain.CraftNoteRecord;
import com.orbitworkbench.craft.infrastructure.mapper.CraftNoteMapper;
import com.orbitworkbench.interview.domain.ReportCenterRow;
import com.orbitworkbench.interview.infrastructure.mapper.InterviewReportMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * V51：面试弱项 → 具体套路推荐（报告 → 本事库的横向连接）。
 *
 * <p>纪律与 V48 相同：纯关键词派生，不调模型、不落库——推荐结论必须能从
 * 报告维度分与套路文本里复算出来，匹配不上就如实说匹配不上，不硬凑。
 *
 * <p>思路：取最近一次出分报告中 &lt;{@value #WEAK_SCORE_THRESHOLD} 分的维度
 * （低分优先，最多 {@value #MAX_DIMENSIONS} 个），每个维度拿自己的关键词表去
 * 已确认套路的「标题 + 场景 + 正文 + 标签」里找命中最多的那条；同一套路
 * 只推荐一次（给最弱的维度），已练熟的套路不再占推荐位。
 */
@Service
public class CraftRecommendationService {

    private static final int WEAK_SCORE_THRESHOLD = 70;
    private static final int MAX_DIMENSIONS = 3;
    private static final int RECENT_REPORT_LIMIT = 5;

    /** 维度 → 套路文本关键词。与 11 维评分口径（v1 归档 06）对齐，只列可被套路命中的词。 */
    private static final Map<String, Set<String>> DIMENSION_KEYWORDS = Map.ofEntries(
            Map.entry("排障与异常恢复", Set.of("排查", "故障", "异常", "事故", "定位", "恢复", "兜底")),
            Map.entry("表达结构", Set.of("讲述", "表达", "结构化", "话术", "STAR", "项目介绍", "沟通")),
            Map.entry("项目实践能力", Set.of("项目", "落地", "实践", "上线", "交付")),
            Map.entry("架构取舍", Set.of("架构", "取舍", "选型", "方案对比")),
            Map.entry("原理理解", Set.of("原理", "底层", "机制", "源码")),
            Map.entry("实现深度", Set.of("实现", "编码", "性能", "调优")),
            Map.entry("业务理解", Set.of("业务", "价值", "场景")),
            Map.entry("边界意识", Set.of("边界", "并发", "幂等", "异常输入")),
            Map.entry("问题分析", Set.of("分析", "拆解", "假设", "验证")),
            Map.entry("技术正确性", Set.of("验证", "测试", "回归", "正确")),
            Map.entry("方案完整性", Set.of("方案", "完整性", "权衡", "备选")));

    private final InterviewReportMapper reportMapper;
    private final CraftNoteMapper craftMapper;
    private final ObjectMapper objectMapper;

    public CraftRecommendationService(InterviewReportMapper reportMapper,
                                      CraftNoteMapper craftMapper,
                                      ObjectMapper objectMapper) {
        this.reportMapper = reportMapper;
        this.craftMapper = craftMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public CraftRecommendationsResponse recommend(Long userId) {
        ReportCenterRow latest = latestReadyReport(userId);
        if (latest == null) {
            return new CraftRecommendationsResponse(List.of(),
                    "还没有出分的面试报告——先做一次模拟面试，报告里才知道哪个维度弱。");
        }
        List<Map.Entry<String, Integer>> weak = weakDimensions(latest);
        if (weak.isEmpty()) {
            return new CraftRecommendationsResponse(List.of(),
                    "最近一次面试「" + latest.getSessionTitle() + "」各维度都在 "
                            + WEAK_SCORE_THRESHOLD + " 分以上，暂无需要补的弱项。");
        }
        List<CraftNoteRecord> pool = new ArrayList<>(craftMapper.listConfirmedForPrompt(userId));
        List<WeaknessCraftRecommendation> items = new ArrayList<>();
        Set<Long> used = new java.util.HashSet<>();
        for (Map.Entry<String, Integer> dimension : weak) {
            CraftNoteRecord best = bestMatch(pool, used, dimension.getKey());
            if (best == null) {
                continue;
            }
            used.add(best.getId());
            items.add(new WeaknessCraftRecommendation(dimension.getKey(), dimension.getValue(),
                    best.getId(), best.getTitle(), best.getCategory(), best.getWhenToUse(),
                    best.getPracticeCount() >= 1));
        }
        if (items.isEmpty()) {
            return new CraftRecommendationsResponse(List.of(),
                    "弱项是「" + joinDimensions(weak) + "」，但本事库里没有对得上的套路——"
                            + "可以点「提炼套路」从工作记录里长一条出来，或手动录入。");
        }
        return new CraftRecommendationsResponse(items,
                "依据最近一次面试「" + latest.getSessionTitle() + "」的维度得分推荐。");
    }

    /** 最近一次 REPORT_READY 报告（listByUser 已按生成时间倒序）。 */
    private ReportCenterRow latestReadyReport(Long userId) {
        return reportMapper.listByUser(userId, null, null, null, null, null, RECENT_REPORT_LIMIT, 0)
                .stream()
                .filter(row -> "REPORT_READY".equals(row.getReportStatus()))
                .findFirst()
                .orElse(null);
    }

    /** 维度得分 &lt; 阈值，按分数升序，最多 MAX_DIMENSIONS 个。 */
    private List<Map.Entry<String, Integer>> weakDimensions(ReportCenterRow row) {
        String json = row.getDimensionScoresJson();
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.isObject()) {
                return List.of();
            }
            Map<String, Integer> scores = new LinkedHashMap<>();
            node.fields().forEachRemaining(entry -> scores.put(entry.getKey(), entry.getValue().asInt()));
            return scores.entrySet().stream()
                    .filter(entry -> entry.getValue() < WEAK_SCORE_THRESHOLD)
                    .sorted(Comparator.comparingInt(Map.Entry<String, Integer>::getValue))
                    .limit(MAX_DIMENSIONS)
                    .toList();
        } catch (Exception exception) {
            return List.of();
        }
    }

    /** 命中数最高的未使用套路；同分时按库内顺序（置顶在前）取先者。命中数为 0 不算匹配。 */
    private CraftNoteRecord bestMatch(List<CraftNoteRecord> pool, Set<Long> used, String dimension) {
        Set<String> keywords = DIMENSION_KEYWORDS.get(dimension);
        if (keywords == null || keywords.isEmpty()) {
            return null;
        }
        CraftNoteRecord best = null;
        int bestHits = 0;
        for (CraftNoteRecord craft : pool) {
            if (used.contains(craft.getId()) || craft.getPracticeCount() >= 1) {
                continue; // 已练熟的不再推荐；同一次里不重复推荐
            }
            int hits = hits(keywords, craft);
            if (hits > bestHits) {
                best = craft;
                bestHits = hits;
            }
        }
        return best;
    }

    private int hits(Set<String> keywords, CraftNoteRecord craft) {
        String haystack = (craft.getTitle() + " " + craft.getWhenToUse() + " "
                + craft.getContent() + " " + craft.getTagsJson()).toLowerCase();
        int hits = 0;
        for (String keyword : keywords) {
            if (haystack.contains(keyword.toLowerCase())) {
                hits += 1;
            }
        }
        return hits;
    }

    private String joinDimensions(List<Map.Entry<String, Integer>> weak) {
        return weak.stream()
                .map(entry -> entry.getKey() + "（" + entry.getValue() + " 分）")
                .reduce((a, b) -> a + "、" + b)
                .orElse("");
    }
}
