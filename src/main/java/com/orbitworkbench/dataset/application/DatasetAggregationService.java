package com.orbitworkbench.dataset.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.dataset.domain.DatasetColumnRecord;
import com.orbitworkbench.dataset.domain.DatasetRecord;
import com.orbitworkbench.dataset.domain.DatasetSheetRecord;
import com.orbitworkbench.dataset.infrastructure.mapper.DatasetMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.tool.application.ToolExecutionContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatasetAggregationService {

    private static final Set<String> OPERATIONS =
            Set.of("count", "distinct_count", "sum", "avg", "min", "max");
    private static final int MAX_GROUPS = 10_000;

    private final DatasetService datasetService;
    private final DatasetMapper datasetMapper;
    private final DatasetRowScanService rowScanService;
    private final ObjectMapper objectMapper;

    public DatasetAggregationService(DatasetService datasetService,
                                     DatasetMapper datasetMapper,
                                     DatasetRowScanService rowScanService,
                                     ObjectMapper objectMapper) {
        this.datasetService = datasetService;
        this.datasetMapper = datasetMapper;
        this.rowScanService = rowScanService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Scope requireScope(ToolExecutionContext context) {
        DatasetRecord dataset = datasetService.requireReadyDataset(context.datasetId());
        if (!dataset.getWorkspaceId().equals(context.workspaceId())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    ErrorCode.DATASET_ACCESS_DENIED,
                    "数据集不属于当前工作空间");
        }
        DatasetSheetRecord sheet = datasetMapper.findSheet(
                context.datasetId(), context.sheetId());
        if (sheet == null) {
            throw invalid("数据集工作表不存在");
        }
        List<DatasetColumnRecord> columns = datasetMapper.findColumns(
                context.datasetId(), context.sheetId());
        return new Scope(dataset, sheet, applyColumnOverrides(columns, context.columnOverrides()));
    }

    public JsonNode aggregate(ToolExecutionContext context, JsonNode arguments) {
        Scope scope = requireScope(context);
        String operation = requiredText(arguments, "operation").toLowerCase(Locale.ROOT);
        if (!OPERATIONS.contains(operation)) {
            throw invalid("operation 不受支持");
        }
        String field = optionalText(arguments, "field");
        List<String> groupBy = stringList(arguments.get("groupBy"));
        if (groupBy.size() > 2) {
            throw invalid("groupBy 最多包含两个字段");
        }
        int limit = arguments.has("limit") ? arguments.get("limit").asInt() : 20;
        limit = Math.min(Math.max(limit, 1), 100);
        String sort = optionalText(arguments, "sort");
        sort = sort == null ? "DESC" : sort.toUpperCase(Locale.ROOT);
        if (!Set.of("ASC", "DESC").contains(sort)) {
            throw invalid("sort 必须为 ASC 或 DESC");
        }

        ColumnRef valueColumn = field == null
                ? null : resolveColumn(scope.columns(), field);
        if (!"count".equals(operation) && valueColumn == null) {
            throw invalid(operation + " 必须指定 field");
        }
        List<ColumnRef> groupColumns = groupBy.stream()
                .map(name -> resolveColumn(scope.columns(), name))
                .toList();
        if (Set.of("sum", "avg").contains(operation)
                && !valueColumn.numeric()) {
            throw invalid(operation + " 只支持数值字段");
        }

        Map<GroupKey, Accumulator> groups = new LinkedHashMap<>();
        rowScanService.scan(
                scope.dataset(),
                scope.sheet(),
                scope.columns(),
                row -> {
                    GroupKey key = groupKey(row, groupColumns);
                    Accumulator accumulator = groups.computeIfAbsent(key, ignored -> {
                        if (groups.size() >= MAX_GROUPS) {
                            throw new ApiException(
                                    HttpStatus.PAYLOAD_TOO_LARGE,
                                    ErrorCode.TOOL_RESULT_TOO_LARGE,
                                    "聚合分组数量超过限制");
                        }
                        return new Accumulator(operation, valueColumn);
                    });
                    accumulator.accept(row);
                });
        if (groups.isEmpty() && groupColumns.isEmpty()) {
            groups.put(new GroupKey(List.of()), new Accumulator(operation, valueColumn));
        }

        List<ResultRow> rows = new ArrayList<>();
        for (Map.Entry<GroupKey, Accumulator> entry : groups.entrySet()) {
            Map<String, String> groupValues = new LinkedHashMap<>();
            for (int index = 0; index < groupColumns.size(); index++) {
                groupValues.put(
                        groupColumns.get(index).column().getNormalizedName(),
                        entry.getKey().values().get(index));
            }
            rows.add(new ResultRow(
                    groupValues,
                    entry.getValue().value(),
                    entry.getValue().observedRows,
                    entry.getValue().ignoredRows));
        }
        Comparator<ResultRow> comparator =
                Comparator.comparing(ResultRow::value, this::compareValues);
        if ("DESC".equals(sort)) {
            comparator = comparator.reversed();
        }
        rows.sort(comparator.thenComparing(row -> row.groups().toString()));
        boolean truncated = rows.size() > limit;
        List<ResultRow> limited = rows.subList(0, Math.min(rows.size(), limit));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("datasetId", context.datasetId());
        result.put("sheetId", context.sheetId());
        result.put("operation", operation);
        result.put("field", valueColumn == null
                ? null : valueColumn.column().getNormalizedName());
        result.put("groupBy", groupColumns.stream()
                .map(column -> column.column().getNormalizedName())
                .toList());
        result.put("rows", limited);
        result.put("truncated", truncated);
        result.put("groupCount", rows.size());
        return objectMapper.valueToTree(result);
    }

    private GroupKey groupKey(List<String> row, List<ColumnRef> groupColumns) {
        if (groupColumns.isEmpty()) {
            return new GroupKey(List.of());
        }
        return new GroupKey(groupColumns.stream()
                .map(column -> valueAt(row, column.index()))
                .toList());
    }

    private ColumnRef resolveColumn(List<DatasetColumnRecord> columns, String requested) {
        String normalized = requested.trim();
        for (int index = 0; index < columns.size(); index++) {
            DatasetColumnRecord column = columns.get(index);
            if (normalized.equalsIgnoreCase(column.getColumnName())
                    || normalized.equalsIgnoreCase(column.getNormalizedName())) {
                return new ColumnRef(
                        index,
                        column,
                        Set.of("INTEGER", "DECIMAL").contains(column.getEffectiveType()));
            }
        }
        throw invalid("字段不存在: " + normalized);
    }

    private List<DatasetColumnRecord> applyColumnOverrides(
            List<DatasetColumnRecord> columns,
            Map<Long, String> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return columns;
        }
        return columns.stream()
                .map(column -> {
                    String override = overrides.get(column.getId());
                    if (override == null || override.isBlank()) {
                        return column;
                    }
                    DatasetColumnRecord effective = copyColumn(column);
                    effective.setEffectiveType(override);
                    return effective;
                })
                .toList();
    }

    private DatasetColumnRecord copyColumn(DatasetColumnRecord source) {
        DatasetColumnRecord target = new DatasetColumnRecord();
        target.setId(source.getId());
        target.setDatasetId(source.getDatasetId());
        target.setSheetId(source.getSheetId());
        target.setOrdinalPosition(source.getOrdinalPosition());
        target.setColumnName(source.getColumnName());
        target.setNormalizedName(source.getNormalizedName());
        target.setInferredType(source.getInferredType());
        target.setEffectiveType(source.getEffectiveType());
        target.setNullable(source.isNullable());
        target.setSampleValuesJson(source.getSampleValuesJson());
        target.setVersion(source.getVersion());
        target.setCreatedAt(source.getCreatedAt());
        target.setUpdatedAt(source.getUpdatedAt());
        return target;
    }

    private String requiredText(JsonNode arguments, String field) {
        String value = optionalText(arguments, field);
        if (value == null) {
            throw invalid(field + " 不能为空");
        }
        return value;
    }

    private String optionalText(JsonNode arguments, String field) {
        JsonNode value = arguments.get(field);
        if (value == null || value.isNull() || !value.isTextual()
                || value.asText().isBlank()) {
            return null;
        }
        return value.asText().trim();
    }

    private List<String> stringList(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw invalid("groupBy 必须为数组");
        }
        List<String> result = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isTextual() || item.asText().isBlank()) {
                throw invalid("groupBy 字段不能为空");
            }
            result.add(item.asText().trim());
        }
        return List.copyOf(result);
    }

    private int compareValues(Object left, Object right) {
        if (left == right) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        if (left instanceof BigDecimal leftNumber
                && right instanceof BigDecimal rightNumber) {
            return leftNumber.compareTo(rightNumber);
        }
        if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
            return BigDecimal.valueOf(leftNumber.doubleValue())
                    .compareTo(BigDecimal.valueOf(rightNumber.doubleValue()));
        }
        return left.toString().compareTo(right.toString());
    }

    private String valueAt(List<String> row, int index) {
        return index < row.size() && row.get(index) != null
                ? row.get(index) : "";
    }

    private ApiException invalid(String message) {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                ErrorCode.TOOL_CALL_INVALID,
                message);
    }

    public record Scope(
            DatasetRecord dataset,
            DatasetSheetRecord sheet,
            List<DatasetColumnRecord> columns
    ) {
    }

    private record ColumnRef(
            int index,
            DatasetColumnRecord column,
            boolean numeric
    ) {
    }

    private record GroupKey(List<String> values) {
    }

    public record ResultRow(
            Map<String, String> groups,
            Object value,
            long observedRows,
            long ignoredRows
    ) {
    }

    private final class Accumulator {
        private final String operation;
        private final ColumnRef column;
        private final Set<String> distinct = new HashSet<>();
        private long observedRows;
        private long ignoredRows;
        private BigDecimal sum = BigDecimal.ZERO;
        private BigDecimal numericMin;
        private BigDecimal numericMax;
        private String textMin;
        private String textMax;

        private Accumulator(String operation, ColumnRef column) {
            this.operation = operation;
            this.column = column;
        }

        private void accept(List<String> row) {
            if ("count".equals(operation) && column == null) {
                observedRows++;
                return;
            }
            String value = valueAt(row, column.index()).trim();
            if (value.isEmpty()) {
                ignoredRows++;
                return;
            }
            observedRows++;
            if ("count".equals(operation)) {
                return;
            }
            if ("distinct_count".equals(operation)) {
                distinct.add(value);
                return;
            }
            if (column.numeric()) {
                try {
                    BigDecimal numeric = new BigDecimal(value);
                    sum = sum.add(numeric);
                    numericMin = numericMin == null ? numeric : numericMin.min(numeric);
                    numericMax = numericMax == null ? numeric : numericMax.max(numeric);
                } catch (NumberFormatException exception) {
                    ignoredRows++;
                    observedRows--;
                }
                return;
            }
            textMin = textMin == null || value.compareTo(textMin) < 0 ? value : textMin;
            textMax = textMax == null || value.compareTo(textMax) > 0 ? value : textMax;
        }

        private Object value() {
            return switch (operation) {
                case "count" -> observedRows;
                case "distinct_count" -> distinct.size();
                case "sum" -> sum;
                case "avg" -> observedRows == 0
                        ? null
                        : sum.divide(
                                BigDecimal.valueOf(observedRows),
                                8,
                                RoundingMode.HALF_UP);
                case "min" -> column.numeric() ? numericMin : textMin;
                case "max" -> column.numeric() ? numericMax : textMax;
                default -> null;
            };
        }
    }
}
