package com.orbitworkbench.ai.application;

import java.util.List;

/**
 * 一次 AI 调用实际注入的个人记忆上下文（V45 注入溯源，借鉴 EvoFlow 资产引用纪律）。
 *
 * <p>「模型到底看到了什么记忆」不能是黑盒：确认了事实但注入没生效、快照过期回退逐条，
 * 事后都要能从审计里回答。text 进 prompt，其余字段进配置快照的 memory 键——
 * 只记模式/条数/事实 id/过时数与长度，不记快照正文与事实内容（同审计不落请求正文的纪律）。
 *
 * @param text       注入 prompt 的记忆块正文；NONE 时为空串
 * @param mode       DIGEST（编译快照）/ FACTS（逐条事实）/ NONE（无记忆注入）
 * @param factIds    本次模型实际「看到」的画像事实 id（DIGEST 为快照编译来源集）
 * @param staleCount FACTS 模式下带「可能已过时」标注的条数；DIGEST 恒为 0
 */
public record MemoryContext(String text, String mode, List<Long> factIds, int staleCount) {

    public static final MemoryContext NONE = new MemoryContext("", "NONE", List.of(), 0);

    public boolean present() {
        return !"NONE".equals(mode);
    }

    public int chars() {
        return text == null ? 0 : text.length();
    }
}
