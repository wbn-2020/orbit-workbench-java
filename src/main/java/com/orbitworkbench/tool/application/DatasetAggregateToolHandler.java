package com.orbitworkbench.tool.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.orbitworkbench.dataset.application.DatasetAggregationService;
import org.springframework.stereotype.Component;

@Component
public class DatasetAggregateToolHandler implements ToolHandler {

    private final DatasetAggregationService aggregationService;

    public DatasetAggregateToolHandler(DatasetAggregationService aggregationService) {
        this.aggregationService = aggregationService;
    }

    @Override
    public String toolCode() {
        return "dataset.aggregate";
    }

    @Override
    public ToolExecutionResult execute(ToolExecutionContext context, JsonNode arguments) {
        JsonNode result = aggregationService.aggregate(context, arguments);
        int groupCount = result.path("groupCount").asInt();
        return new ToolExecutionResult(
                result,
                "完成聚合并返回 " + groupCount + " 个分组");
    }
}
