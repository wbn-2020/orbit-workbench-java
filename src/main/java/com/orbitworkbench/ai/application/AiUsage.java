package com.orbitworkbench.ai.application;

/**
 * 一次模型调用的 token 用量。明细字段允许 null：上游没报就是不知道，不拿 0 冒充（V40/V42 口径）。
 *
 * @param cachedInputTokens     命中隐式缓存的输入 token（qwen: prompt_tokens_details.cached_tokens），
 *                              计费单价低于常规输入价；inputTokens 仍是总量，缓存是其中的子集。
 * @param reasoningOutputTokens 输出中属于推理的部分（completion_tokens_details.reasoning_tokens），
 *                              仅展示明细，单价与常规输出相同。
 */
public record AiUsage(Integer inputTokens,
                      Integer outputTokens,
                      Integer totalTokens,
                      Integer cachedInputTokens,
                      Integer reasoningOutputTokens) {

    public AiUsage(Integer inputTokens, Integer outputTokens, Integer totalTokens) {
        this(inputTokens, outputTokens, totalTokens, null, null);
    }
}
