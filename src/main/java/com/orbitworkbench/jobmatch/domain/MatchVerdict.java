package com.orbitworkbench.jobmatch.domain;

/** 单条要求的判定结果，对应 `11` §5.4 要求的三栏。 */
public enum MatchVerdict {

    MATCHED("已具备"),
    GAP("待补齐"),
    NEED_CONFIRMATION("需人工确认");

    private final String displayName;

    MatchVerdict(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
