package com.orbitworkbench.workbench.api;

import java.util.List;

/** 工作台首页聚合响应结构（v2）。 */
public final class WorkbenchDtos {

    private WorkbenchDtos() {
    }

    public record ModeCard(
            String key,
            String title,
            String description,
            String to,
            String metricLabel,
            String metricValue
    ) {}

    public record AssetCount(
            String key,
            String label,
            String to,
            long count
    ) {}

    public record AgendaItem(
            String id,
            String time,
            String title,
            boolean done
    ) {}

    /**
     * 管线体检单项（借鉴 EvoFlow 运营洞察：链路哪里卡住要看得见）。
     * status 四档：BLOCK 阻断（不修没法继续）/ ACTION 有卡点待处理 /
     * STALE 节奏脱期（信息性）/ OK 正常。detail 里只放可数的真实数字。
     */
    public record PipelineCheck(
            String key,
            String status,
            String title,
            String detail,
            String to,
            String action
    ) {}

    public record WorkbenchSummaryResponse(
            String greeting,
            List<ModeCard> modes,
            List<AssetCount> assets,
            List<AgendaItem> agenda,
            int focusTodayMinutes,
            List<PipelineCheck> pipeline
    ) {}
}
