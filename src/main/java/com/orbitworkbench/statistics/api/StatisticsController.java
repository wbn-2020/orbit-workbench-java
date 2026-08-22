package com.orbitworkbench.statistics.api;

import com.orbitworkbench.statistics.api.StatisticsDtos.ModelUsageResponse;
import com.orbitworkbench.statistics.application.ModelUsageStatisticsService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/statistics")
public class StatisticsController {

    private final ModelUsageStatisticsService modelUsageStatisticsService;

    public StatisticsController(ModelUsageStatisticsService modelUsageStatisticsService) {
        this.modelUsageStatisticsService = modelUsageStatisticsService;
    }

    @GetMapping("/model-usage")
    public ModelUsageResponse modelUsage(
            @RequestParam Long workspaceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return modelUsageStatisticsService.getModelUsage(workspaceId, from, to);
    }
}
