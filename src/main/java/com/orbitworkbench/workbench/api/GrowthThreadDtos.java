package com.orbitworkbench.workbench.api;

import java.util.List;

/** 成长脉络（V55）响应结构：已存在的溯源链接聚合成的「链」，纯只读。 */
public final class GrowthThreadDtos {

    private GrowthThreadDtos() {
    }

    /** 链上一步：kind 是节点类型（FACT/GOAL/CRAFT/TASK/REPORT），status 为人话状态。 */
    public record ThreadStep(
            String kind,
            Long refId,
            String title,
            String status,
            String to
    ) {}

    /** 一条成长线索：origin=起点（事实/套路/报告），steps=链上已发生的后续。 */
    public record GrowthThread(
            String type,
            List<ThreadStep> origin,
            List<ThreadStep> steps,
            /** V54 效果状态（仅套路链练熟后存在）：IMPROVED/DECLINED/FLAT/INSUFFICIENT。 */
            String effect
    ) {}

    public record GrowthThreadsResponse(List<GrowthThread> items) {}
}
