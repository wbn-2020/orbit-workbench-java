package com.orbitworkbench.ai.application;

import java.util.Locale;

/**
 * 联网检索的请求形状（ADR-0012 决策 2）。
 *
 * <p>同一种协议下各家的搜索参数名完全不同，所以"能不能联网"不能是一个布尔值：
 * 连接声明的是<b>按哪种形状发</b>，适配器只管照形状填参数。界面也只有一个下拉框，
 * 勾不出"支持强制检索但根本不会搜"这种自相矛盾的组合。
 *
 * <p>{@code NONE} 是默认值——未声明的连接永远不联网。
 */
public enum WebSearchDialect {
    /** 不联网。 */
    NONE(false),
    /** Responses：{@code tools:[{"type":"web_search"}]}，只能由模型自决，无法强制必搜（能强制的是下一个方言）。 */
    RESPONSES_TOOL(false),
    /** Responses 且可用 {@code tool_choice} 强制必搜（实测 DeepSeek Responses 支持）。 */
    RESPONSES_TOOL_FORCED(true),
    /** Chat Completions：顶层 {@code web_search_options}（OpenAI 搜索专用预览模型）。 */
    OPENAI_CHAT_WEB_SEARCH_OPTIONS(false),
    /** 兼容 Chat：顶层 {@code enable_search} + {@code search_options.forced_search}（阿里云 Qwen）。 */
    QWEN_CHAT_ENABLE_SEARCH(true),
    /** xAI Chat：顶层 {@code search_parameters.mode} = auto / on。 */
    XAI_CHAT_SEARCH_PARAMETERS(true);

    private final boolean forcedSearchSupported;

    WebSearchDialect(boolean forcedSearchSupported) {
        this.forcedSearchSupported = forcedSearchSupported;
    }

    /** 「必搜」能不能表达。AUTO 不要求这个，ON_DEMAND 要求。 */
    public boolean supportsForcedSearch() {
        return forcedSearchSupported;
    }

    public boolean supportsWebSearch() {
        return this != NONE;
    }

    public static WebSearchDialect parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return NONE;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknownDialect) {
            // 读不出来的方言按不联网处理：宁可功能不可见，也不要往上游发它不认的参数。
            return NONE;
        }
    }
}
