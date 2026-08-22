package com.orbitworkbench.tool.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.orbitworkbench.dataset.application.DatasetAggregationService;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class DatasetChartSpecToolHandler implements ToolHandler {

    private static final Set<String> CHART_TYPES = Set.of("bar", "line", "pie");

    private final DatasetAggregationService aggregationService;
    private final ObjectMapper objectMapper;

    public DatasetChartSpecToolHandler(DatasetAggregationService aggregationService,
                                       ObjectMapper objectMapper) {
        this.aggregationService = aggregationService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String toolCode() {
        return "dataset.chart_spec";
    }

    @Override
    public ToolExecutionResult execute(ToolExecutionContext context, JsonNode arguments) {
        String chartType = arguments.path("chartType").asText();
        if (!CHART_TYPES.contains(chartType)) {
            throw invalid("chartType 不受支持");
        }
        String categoryField = arguments.path("categoryField").asText();
        String operation = arguments.path("operation").asText();
        ObjectNode aggregateArguments = objectMapper.createObjectNode();
        aggregateArguments.put("operation", operation);
        if (arguments.hasNonNull("valueField")) {
            aggregateArguments.put("field", arguments.get("valueField").asText());
        }
        ArrayNode groupBy = aggregateArguments.putArray("groupBy");
        groupBy.add(categoryField);
        aggregateArguments.put("sort", "DESC");
        aggregateArguments.put(
                "limit",
                arguments.has("limit") ? arguments.get("limit").asInt() : 20);
        JsonNode aggregate = aggregationService.aggregate(context, aggregateArguments);

        ObjectNode spec = objectMapper.createObjectNode();
        spec.put("specVersion", 1);
        spec.put("renderer", "echarts");
        spec.put("chartType", chartType);
        spec.put("datasetId", context.datasetId());
        spec.put("sheetId", context.sheetId());
        ObjectNode option = spec.putObject("option");
        if (arguments.hasNonNull("title")) {
            option.putObject("title").put("text", arguments.get("title").asText());
        }
        option.putObject("tooltip").put(
                "trigger", "pie".equals(chartType) ? "item" : "axis");
        ArrayNode rows = (ArrayNode) aggregate.path("rows");
        if ("pie".equals(chartType)) {
            ArrayNode data = objectMapper.createArrayNode();
            rows.forEach(row -> {
                ObjectNode item = data.addObject();
                item.put("name", firstGroupValue(row.path("groups")));
                item.set("value", row.get("value"));
            });
            option.putArray("series")
                    .addObject()
                    .put("type", "pie")
                    .set("data", data);
        } else {
            ArrayNode categories = option.putObject("xAxis")
                    .put("type", "category")
                    .putArray("data");
            ArrayNode values = objectMapper.createArrayNode();
            rows.forEach(row -> {
                categories.add(firstGroupValue(row.path("groups")));
                values.add(row.get("value"));
            });
            option.putObject("yAxis").put("type", "value");
            option.putArray("series")
                    .addObject()
                    .put("type", chartType)
                    .set("data", values);
        }
        spec.set("source", aggregate);
        return new ToolExecutionResult(
                spec,
                "生成 " + chartType + " 图表规格");
    }

    private String firstGroupValue(JsonNode groups) {
        if (groups == null || !groups.isObject()) {
            return "";
        }
        var values = groups.elements();
        return values.hasNext() ? values.next().asText() : "";
    }

    private ApiException invalid(String message) {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                ErrorCode.TOOL_CALL_INVALID,
                message);
    }
}
