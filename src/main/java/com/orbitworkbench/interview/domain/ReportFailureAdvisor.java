package com.orbitworkbench.interview.domain;

/**
 * 报告失败归因（纯派生，不新增任何存储或调用）。
 *
 * <p>{@code failure_reason} 落库的是 AiErrorSanitizer 脱敏后的错误摘要，
 * 里面嵌着 ErrorCode 名或解析失败原因；对用户来说仍然太术语。归因器把已知
 * 形态翻译成「发生了什么 + 下一步做什么」。识别不了就返回通用建议——
 * 不猜测具体原因，宁缺毋假。
 *
 * <p>重试次数参与建议：同一报告反复失败时，「再试一次」大概率不是答案，
 * 换账户才是（对齐 EvoFlow plan-before-execute 的思路：行动前先给出计划性建议）。
 */
public final class ReportFailureAdvisor {

    /** 超过该重试次数后，建议从「直接重试」升级为「换账户/换模型」。 */
    public static final int SWITCH_AFTER_RETRIES = 2;

    private ReportFailureAdvisor() {
    }

    public record Advice(String summary, String nextStep) {}

    /** failureReason 为空（非失败态）返回 null；失败态永远给出通用兜底。 */
    public static Advice advise(String failureReason, int retryCount) {
        if (failureReason == null || failureReason.isBlank()) {
            return null;
        }
        String text = failureReason;
        String summary;
        String base;
        if (text.contains("RATE_LIMITED")) {
            summary = "模型服务商限流";
            base = "稍等一会儿再重试；频繁出现可换一个未被限流的账户";
        } else if (text.contains("REQUEST_TIMEOUT") || text.contains("Timeout")) {
            summary = "模型响应超时";
            base = "可直接重试；反复超时可换响应更快的账户";
        } else if (text.contains("AUTHENTICATION_FAILED") || text.contains("认证")) {
            summary = "账户凭据被拒";
            base = "到「AI 连接」检查该账户的 API Key 是否失效";
        } else if (text.contains("UNSUPPORTED_CAPABILITY") || text.contains("不支持")) {
            summary = "所选账户不支持所需能力";
            base = "换一个支持该能力的账户（见「AI 连接」）";
        } else if (text.contains("INVALID_STRUCTURED_OUTPUT") || text.contains("JSON")
                || text.contains("totalScore") || text.contains("hiringRecommendation")
                || text.contains("dimensionScores")) {
            summary = "模型返回的评分结构不合规则";
            base = "多数是偶然的输出抖动，直接重试通常就好";
        } else if (text.contains("UPSTREAM_UNAVAILABLE") || text.contains("调用未完成")
                || text.contains("调用失败")) {
            summary = "模型上游调用失败";
            base = "可直接重试；持续失败检查网络或换账户";
        } else {
            summary = "报告生成失败";
            base = "可直接重试，失败原因已完整记录";
        }
        String nextStep = retryCount >= SWITCH_AFTER_RETRIES
                ? base + "。已连续失败 " + retryCount + " 次，建议优先换账户再重试"
                : base;
        return new Advice(summary, nextStep);
    }
}
