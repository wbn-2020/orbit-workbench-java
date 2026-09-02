package com.orbitworkbench.aiconnection.domain;

import com.orbitworkbench.ai.application.WebSearchDialect;
import java.util.Locale;

/**
 * 模型能力声明（{@code model_profile.capabilities_json}）。
 *
 * <p>这一列从 V2 起就在，但 {@code AiConnectionService.newRecord} 一直只写死
 * {@code {"supportsStreaming":true}}，联网能力此前无处表达。
 *
 * <p>解析故意不用 JSON 库而只做键值扫描：这个列的文本由本服务自己生成（见 {@link #toJson}），
 * 而解析失败的方向必须是<b>不联网</b>——把畸形 JSON 读成"能搜"会让一条其实不支持的连接
 * 开始往上游发它不认的参数，那属于静默错误；读成 NONE 只是功能不可用，用户看得见。
 */
public record ModelCapabilities(WebSearchDialect webSearchDialect) {

    private static final String WEB_SEARCH_DIALECT_KEY = "\"webSearchDialect\"";

    public static final ModelCapabilities NONE = new ModelCapabilities(WebSearchDialect.NONE);

    public ModelCapabilities {
        webSearchDialect = webSearchDialect == null ? WebSearchDialect.NONE : webSearchDialect;
    }

    public static ModelCapabilities parse(String json) {
        if (json == null || json.isBlank()) {
            return NONE;
        }
        // 扫描前统一转小写，因此要查的键名也必须转小写——否则驼峰键永远匹配不上，
        // 声明会被读成 NONE（运行验收实测抓到过一次）。
        String normalized = json.toLowerCase(Locale.ROOT);
        String value = stringValue(normalized, WEB_SEARCH_DIALECT_KEY.toLowerCase(Locale.ROOT));
        return new ModelCapabilities(WebSearchDialect.parse(value));
    }

    public String toJson() {
        return "{\"supportsStreaming\":true,\"webSearchDialect\":\"" + webSearchDialect.name() + "\"}";
    }

    private static String stringValue(String normalizedJson, String key) {
        int at = normalizedJson.indexOf(key);
        if (at < 0) {
            return null;
        }
        int colon = normalizedJson.indexOf(':', at + key.length());
        int open = normalizedJson.indexOf('"', colon + 1);
        if (colon < 0 || open < 0) {
            return null;
        }
        int close = normalizedJson.indexOf('"', open + 1);
        return close < 0 ? null : normalizedJson.substring(open + 1, close);
    }
}
