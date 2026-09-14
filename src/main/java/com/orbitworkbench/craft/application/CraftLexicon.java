package com.orbitworkbench.craft.application;

import com.orbitworkbench.craft.domain.CraftNoteRecord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 维度 ↔ 套路文本的共享词典（V51 抽取，V54 复用）。
 *
 * <p>与 11 维评分口径（v1 归档 06）对齐，只列可被套路文本命中的词。
 * 抽成单一来源是为了两个方向的判定永不漂移：
 * V51「弱项 → 推荐哪条套路」与 V54「练熟的套路 → 关联哪些维度」
 * 用的是同一张表同一套命中算法，否则推荐与证据会自相矛盾。
 */
final class CraftLexicon {

    /** 维度 → 套路文本关键词。 */
    static final Map<String, Set<String>> DIMENSION_KEYWORDS = Map.ofEntries(
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

    private CraftLexicon() {
    }

    /** 套路文本对某维度的关键词命中数（标题+场景+正文+标签）。 */
    static int hits(Set<String> keywords, CraftNoteRecord craft) {
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

    /**
     * 套路可能补强的维度：命中数 &gt; 0 的全部维度，按命中数降序、同分按维度名定序。
     * V54 用它圈定「练熟这条套路后，哪些维度的分数变化可以讲成它的效果候选」。
     */
    static List<String> dimensionsFor(CraftNoteRecord craft) {
        List<Map.Entry<String, Integer>> scored = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : DIMENSION_KEYWORDS.entrySet()) {
            int hits = hits(entry.getValue(), craft);
            if (hits > 0) {
                scored.add(Map.entry(entry.getKey(), hits));
            }
        }
        scored.sort(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed()
                .thenComparing(Map.Entry::getKey));
        return scored.stream().map(Map.Entry::getKey).toList();
    }

    /** 维度分 JSON → 保序 Map；非对象或解析失败返回空（脏数据不进判定）。 */
    static Map<String, Integer> parseScores(com.fasterxml.jackson.databind.ObjectMapper mapper, String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            var node = mapper.readTree(json);
            if (!node.isObject()) {
                return Map.of();
            }
            Map<String, Integer> scores = new java.util.LinkedHashMap<>();
            node.fields().forEachRemaining(entry -> scores.put(entry.getKey(), entry.getValue().asInt()));
            return scores;
        } catch (Exception exception) {
            return Map.of();
        }
    }
}
