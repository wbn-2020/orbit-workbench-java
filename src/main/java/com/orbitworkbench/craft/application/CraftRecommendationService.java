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

    /** 维度 → 套路文本关键词在 CraftLexicon 共享（V54 证据链用同一张表，两方向判定不漂移）。 */

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
        Set<String> keywords = CraftLexicon.DIMENSION_KEYWORDS.get(dimension);
        if (keywords == null || keywords.isEmpty()) {
            return null;
        }
        CraftNoteRecord best = null;
        int bestHits = 0;
        for (CraftNoteRecord craft : pool) {
            if (used.contains(craft.getId()) || craft.getPracticeCount() >= 1) {
                continue; // 已练熟的不再推荐；同一次里不重复推荐
            }
            int hits = CraftLexicon.hits(keywords, craft);
            if (hits > bestHits) {
                best = craft;
                bestHits = hits;
            }
        }
        return best;
    }

    private String joinDimensions(List<Map.Entry<String, Integer>> weak) {
        return weak.stream()
                .map(entry -> entry.getKey() + "（" + entry.getValue() + " 分）")
                .reduce((a, b) -> a + "、" + b)
                .orElse("");
    }
}
