package com.orbitworkbench.jobmatch.domain;

/**
 * JD 要求的类别（`17` §5.1）。类别决定去简历的哪个区块找依据，也决定判不了时归到哪一栏，
 * 因此把区块映射写在枚举里而不是散在服务的 if 里——映射规则就是这套对照的全部可复核性所在。
 */
public enum RequirementCategory {

    SKILL("技能", "SKILLS"),
    EXPERIENCE("经验", "WORK_EXPERIENCE", "EDUCATION"),
    PROJECT("项目", "PROJECT_EXPERIENCE"),
    /** 归不进上面三类的一律不检索任何区块，直接进「需人工确认」：规则判不了就是判不了。 */
    OTHER("其他");

    private final String displayName;
    private final String[] sectionKeys;

    RequirementCategory(String displayName, String... sectionKeys) {
        this.displayName = displayName;
        this.sectionKeys = sectionKeys;
    }

    public String displayName() {
        return displayName;
    }

    /** 参与对照的简历区块 key，顺序即检索顺序；OTHER 为空数组。 */
    public String[] sectionKeys() {
        return sectionKeys;
    }
}
