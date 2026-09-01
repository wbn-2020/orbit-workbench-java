package com.orbitworkbench.capability.domain;

/** 技能图谱的展示分组（`16` §5）。只作归置，不参与任何计算或加权。 */
public enum CapabilityCategory {

    TECH_HARD("技术硬实力"),
    THINKING("思维判断"),
    COMMUNICATION("沟通表达");

    private final String label;

    CapabilityCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
