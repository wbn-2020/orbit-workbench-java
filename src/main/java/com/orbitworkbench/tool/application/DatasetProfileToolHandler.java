package com.orbitworkbench.tool.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.dataset.application.DatasetAggregationService;
import com.orbitworkbench.dataset.application.DatasetService;
import org.springframework.stereotype.Component;

@Component
public class DatasetProfileToolHandler implements ToolHandler {

    private final DatasetAggregationService aggregationService;
    private final DatasetService datasetService;
    private final ObjectMapper objectMapper;

    public DatasetProfileToolHandler(DatasetAggregationService aggregationService,
                                     DatasetService datasetService,
                                     ObjectMapper objectMapper) {
        this.aggregationService = aggregationService;
        this.datasetService = datasetService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String toolCode() {
        return "dataset.profile";
    }

    @Override
    public ToolExecutionResult execute(ToolExecutionContext context, JsonNode arguments) {
        aggregationService.requireScope(context);
        var profile = datasetService.profile(context.datasetId(), context.sheetId());
        return new ToolExecutionResult(
                objectMapper.valueToTree(profile),
                "返回数据质量与字段统计");
    }
}
