package com.orbitworkbench.artifact.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.http.HttpStatus;

public final class ArtifactContentPolicy {

    private static final Set<String> CHART_ROOT_KEYS = Set.of(
            "specVersion", "renderer", "chartType", "datasetId", "sheetId", "option", "source");
    private static final Set<String> CHART_TYPES = Set.of("bar", "line", "pie");
    private static final Set<String> BLOCKED_JSON_KEYS = Set.of(
            "formatter", "renderitem", "transform", "script", "javascript",
            "onclick", "onload", "onerror", "href", "url");
    private static final int MAX_JSON_DEPTH = 16;
    private static final int MAX_JSON_NODES = 20_000;
    private static final long MAX_CSV_RECORDS = 100_001;

    private final ObjectMapper objectMapper;

    public ArtifactContentPolicy(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void validate(String artifactType, String contentFormat, String content) {
        if (content == null) {
            throw invalid("成果内容不能为空");
        }
        switch (contentFormat) {
            case "MARKDOWN" -> validateMarkdown(content);
            case "JSON" -> validateJson(artifactType, content);
            case "CSV" -> validateCsv(content);
            default -> throw invalid("成果格式不受支持");
        }
    }

    private void validateMarkdown(String content) {
        if (content.indexOf('\0') >= 0) {
            throw invalid("Markdown 成果包含非法控制字符");
        }
    }

    private void validateJson(String artifactType, String content) {
        final JsonNode root;
        try {
            root = objectMapper.readTree(content);
        } catch (JsonProcessingException exception) {
            throw invalid("JSON 成果内容无效");
        }
        if (root == null || !root.isObject()) {
            throw invalid("JSON 成果必须是对象");
        }
        if ("CHART_SPEC".equals(artifactType)) {
            validateChartSpec(root);
        }
        walkJson(root, 0, new AtomicInteger(), false);
        if ("CHART_SPEC".equals(artifactType)) {
            walkJson(root.path("option"), 0, new AtomicInteger(), true);
        }
    }

    private void validateChartSpec(JsonNode root) {
        Iterator<String> names = root.fieldNames();
        while (names.hasNext()) {
            String field = names.next();
            if (!CHART_ROOT_KEYS.contains(field)) {
                throw invalid("图表规格包含未允许的顶层字段");
            }
        }
        if (root.path("specVersion").asInt(-1) != 1
                || !"echarts".equals(root.path("renderer").asText())
                || !CHART_TYPES.contains(root.path("chartType").asText())
                || !root.path("datasetId").canConvertToLong()
                || !root.path("sheetId").canConvertToLong()
                || !root.path("option").isObject()
                || !root.path("source").isObject()) {
            throw invalid("图表规格缺少受支持的结构");
        }
        JsonNode series = root.path("option").path("series");
        if (!series.isArray() || series.isEmpty()) {
            throw invalid("图表规格缺少 series");
        }
        for (JsonNode item : series) {
            if (!item.isObject() || !CHART_TYPES.contains(item.path("type").asText())
                    || !item.path("data").isArray()) {
                throw invalid("图表 series 结构不受支持");
            }
        }
    }

    private void walkJson(JsonNode node,
                          int depth,
                          AtomicInteger nodeCount,
                          boolean enforceSafeOptionFields) {
        if (depth > MAX_JSON_DEPTH || nodeCount.incrementAndGet() > MAX_JSON_NODES) {
            throw invalid("JSON 成果结构超过限制");
        }
        if (enforceSafeOptionFields && node.isTextual()) {
            String value = node.asText().toLowerCase(Locale.ROOT);
            if (value.contains("javascript:")
                    || value.contains("<script")
                    || value.contains("<iframe")
                    || value.contains("<img")
                    || value.contains("onerror=")
                    || value.contains("onload=")
                    || value.contains("data:text/html")) {
                throw invalid("JSON 成果包含可执行脚本");
            }
            return;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String normalizedKey = field.getKey().toLowerCase(Locale.ROOT);
                if (enforceSafeOptionFields && BLOCKED_JSON_KEYS.contains(normalizedKey)) {
                    throw invalid("JSON 成果包含未允许的字段");
                }
                walkJson(
                        field.getValue(),
                        depth + 1,
                        nodeCount,
                        enforceSafeOptionFields);
            }
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                walkJson(item, depth + 1, nodeCount, enforceSafeOptionFields);
            }
        }
    }

    private void validateCsv(String content) {
        if (content.indexOf('\0') >= 0) {
            throw invalid("CSV 成果包含非法控制字符");
        }
        try (CSVParser parser = CSVParser.parse(content, CSVFormat.DEFAULT)) {
            long records = 0;
            for (CSVRecord record : parser) {
                if (++records > MAX_CSV_RECORDS) {
                    throw invalid("CSV 成果行数超过限制");
                }
                for (String value : record) {
                    if (isSpreadsheetFormula(value)) {
                        throw invalid("CSV 成果包含潜在公式注入");
                    }
                }
            }
        } catch (IOException exception) {
            throw invalid("CSV 成果内容无效");
        }
    }

    private boolean isSpreadsheetFormula(String value) {
        String normalized = value == null ? "" : value.stripLeading();
        if (normalized.isEmpty()) {
            return false;
        }
        char first = normalized.charAt(0);
        if (first == '=' || first == '+' || first == '@') {
            return true;
        }
        return first == '-' && !normalized.matches("-\\d+(\\.\\d+)?([eE][+-]?\\d+)?");
    }

    private ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, message);
    }
}
