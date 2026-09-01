package com.orbitworkbench.capability.domain;

import java.util.List;
import java.util.Optional;

/**
 * 技能图谱的维度词表（`16` §5）：与 {@code InterviewReportService} 评分提示词固定的 11 项**逐字一致**，
 * 顺序也按提示词顺序，避免每次刷新轴的位置都在动。
 *
 * <p>词表外的名字（例如原型里的「技术深度」「项目实践」「表达沟通」）不参与聚合，也不做名称映射——
 * 映射等于把库里没有的观测画进图里。改评分提示词的维度会推进 {@code scoring_rule_version}，
 * 本枚举必须同步改（`16` §7 的可比较性口径依赖这一点）。
 */
public enum CapabilityDimension {

    BUSINESS_UNDERSTANDING("业务理解", CapabilityCategory.THINKING),
    TECHNICAL_CORRECTNESS("技术正确性", CapabilityCategory.TECH_HARD),
    PRINCIPLE_UNDERSTANDING("原理理解", CapabilityCategory.TECH_HARD),
    IMPLEMENTATION_DEPTH("实现深度", CapabilityCategory.TECH_HARD),
    PROJECT_PRACTICE("项目实践能力", CapabilityCategory.TECH_HARD),
    PROBLEM_ANALYSIS("问题分析", CapabilityCategory.THINKING),
    SOLUTION_COMPLETENESS("方案完整性", CapabilityCategory.THINKING),
    ARCHITECTURE_TRADEOFF("架构取舍", CapabilityCategory.THINKING),
    TROUBLESHOOTING("排障与异常恢复", CapabilityCategory.TECH_HARD),
    STRUCTURED_EXPRESSION("表达结构", CapabilityCategory.COMMUNICATION),
    BOUNDARY_AWARENESS("边界意识", CapabilityCategory.THINKING);

    private static final List<CapabilityDimension> ORDERED = List.of(values());

    private final String displayName;
    private final CapabilityCategory category;

    CapabilityDimension(String displayName, CapabilityCategory category) {
        this.displayName = displayName;
        this.category = category;
    }

    /** 报告 {@code dimension_scores_json} 里的键就是它。 */
    public String displayName() {
        return displayName;
    }

    public CapabilityCategory category() {
        return category;
    }

    /** 提示词顺序，不是按分数排序。 */
    public static List<CapabilityDimension> ordered() {
        return ORDERED;
    }

    public static Optional<CapabilityDimension> findByDisplayName(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String value = raw.trim();
        return ORDERED.stream().filter(dimension -> dimension.displayName.equals(value)).findFirst();
    }
}
