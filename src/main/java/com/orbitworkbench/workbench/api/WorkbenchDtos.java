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

    public record WorkbenchSummaryResponse(
            String greeting,
            List<ModeCard> modes,
            List<AssetCount> assets,
            List<AgendaItem> agenda,
            int focusTodayMinutes
    ) {}
}
