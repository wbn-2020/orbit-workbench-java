package com.orbitworkbench.ai.application;

import com.orbitworkbench.aiconnection.application.AiConnectionService.AiConnectionRuntimeConfig;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 一次调用「请求的联网策略 + 这条连接的能力」得到的结论（ADR-0012 决策 3）。
 *
 * <p>放在 {@code ai} 层是因为两家调用方（面试出题、场景执行）必须得到完全相同的降级语义：
 * 一处静默降级、另一处直接报错，用户就再也摸不到这个开关的边界在哪。
 *
 * @param effective 适配器实际要按哪一档发请求；只会是 DISABLED 或 AUTO
 * @param degraded  请求了联网但本次没联网
 * @param reason    中文说明，进审计快照与界面提示，不给内部码或供应商参数名
 */
public record WebSearchDecision(WebSearchMode effective, boolean degraded, String reason) {

    public boolean applied() {
        return effective != WebSearchMode.DISABLED;
    }

    public static WebSearchDecision resolve(WebSearchMode requested, AiConnectionRuntimeConfig connection) {
        WebSearchMode want = requested == null ? WebSearchMode.DISABLED : requested;
        if (want == WebSearchMode.DISABLED) {
            return new WebSearchDecision(WebSearchMode.DISABLED, false, null);
        }
        WebSearchDialect dialect = connection.webSearchDialect();
        String target = "「" + connection.connectionName() + "」（" + connection.protocol()
                + " / " + connection.modelName() + "）";
        if (want == WebSearchMode.ON_DEMAND) {
            if (!dialect.supportsWebSearch()) {
                throw unsupported(target + " 未声明联网形状，无法满足「必须联网」。可以改用「自动」，"
                        + "或到 AI 连接里确认这条连接的协议与模型到底支持哪种联网方式");
            }
            if (!dialect.supportsForcedSearch()) {
                throw unsupported(target + " 只能由模型自行决定是否检索，无法保证一定联网。"
                        + "可以改用「自动」，或换一条支持强制检索的连接");
            }
        }
        // AUTO 的语义是"要不要搜由模型判断"，连接不支持时退化成模型不搜仍是有效回答，
        // 但必须把退化写进留痕：Qwen 这类网关超限时是静默不搜的，不记就等于下次没人查得出来。
        if (!dialect.supportsWebSearch()) {
            return new WebSearchDecision(WebSearchMode.DISABLED, true,
                    target + " 未声明联网形状，本次按不联网调用");
        }
        return new WebSearchDecision(want, false, null);
    }

    private static ApiException unsupported(String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, ErrorCode.UNSUPPORTED_CAPABILITY, message);
    }
}
