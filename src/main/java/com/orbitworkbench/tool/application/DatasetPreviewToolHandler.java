package com.orbitworkbench.tool.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.dataset.application.DatasetAggregationService;
import com.orbitworkbench.dataset.application.DatasetService;
import org.springframework.stereotype.Component;

@Component
public class DatasetPreviewToolHandler implements ToolHandler {

    private final DatasetAggregationService aggregationService;
    private final DatasetService datasetService;
    private final ObjectMapper objectMapper;

    public DatasetPreviewToolHandler(DatasetAggregationService aggregationService,
                                     DatasetService datasetService,
                                     ObjectMapper objectMapper) {
        this.aggregationService = aggregationService;
        this.datasetService = datasetService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String toolCode() {
        return "dataset.preview";
    }

    @Override
    public ToolExecutionResult execute(ToolExecutionContext context, JsonNode arguments) {
        aggregationService.requireScope(context);
        int offset = arguments.has("offset") ? arguments.get("offset").asInt() : 0;
        int limit = arguments.has("limit") ? arguments.get("limit").asInt() : 20;
        var preview = datasetService.preview(
                context.datasetId(), context.sheetId(), offset, limit);
        return new ToolExecutionResult(
                objectMapper.valueToTree(preview),
                "返回 " + preview.rows().size() + " 行预览");
    }
}
