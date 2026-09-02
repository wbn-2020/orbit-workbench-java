package com.orbitworkbench.ai.application;

import java.util.Locale;

/**
 * 联网检索的统一开关（ADR-0012）。业务侧只说这三个词，供应商参数名一律不出现在这里。
 *
 * <p>{@code ON_DEMAND} 与 {@code AUTO} 的区别不是"搜得多与少"，而是<b>不支持时怎么办</b>：
 * AUTO 可以降级成不搜（模型自己答仍然是有效回答），ON_DEMAND 不行——静默换成不搜会产出
 * 看起来像查过的答案，而这条产品主线靠的是结论可复核。
 */
public enum WebSearchMode {
    DISABLED,
    ON_DEMAND,
    AUTO;

    public static WebSearchMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return DISABLED;
        }
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "ON_DEMAND" -> ON_DEMAND;
            case "AUTO" -> AUTO;
            default -> DISABLED;
        };
    }

    public boolean requested() {
        return this != DISABLED;
    }
}
