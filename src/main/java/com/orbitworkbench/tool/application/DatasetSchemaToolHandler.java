package com.orbitworkbench.tool.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.dataset.application.DatasetAggregationService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class DatasetSchemaToolHandler implements ToolHandler {

    private final DatasetAggregationService aggregationService;
    private final ObjectMapper objectMapper;

    public DatasetSchemaToolHandler(DatasetAggregationService aggregationService,
                                    ObjectMapper objectMapper) {
        this.aggregationService = aggregationService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String toolCode() {
        return "dataset.schema";
    }

    @Override
    public ToolExecutionResult execute(ToolExecutionContext context, JsonNode arguments) {
        var scope = aggregationService.requireScope(context);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("datasetId", context.datasetId());
        result.put("sheetId", context.sheetId());
        result.put("sheetName", scope.sheet().getSheetName());
        result.put("rowCount", scope.sheet().getRowCount());
        result.put("columns", scope.columns().stream()
                .map(column -> Map.of(
                        "name", column.getColumnName(),
                        "normalizedName", column.getNormalizedName(),
                        "type", column.getEffectiveType(),
                        "nullable", column.isNullable()))
                .toList());
        return new ToolExecutionResult(
                objectMapper.valueToTree(result),
                "返回 " + scope.columns().size() + " 个字段");
    }
}
