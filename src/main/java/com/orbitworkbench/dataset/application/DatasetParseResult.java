package com.orbitworkbench.dataset.application;

import java.util.List;
import java.util.Map;

public record DatasetParseResult(List<ParsedSheet> sheets) {

    public record ParsedSheet(
            int sheetIndex,
            String sheetName,
            long rowCount,
            List<ParsedColumn> columns,
            List<List<String>> previewRows,
            Map<String, Object> summary,
            Map<String, Object> quality
    ) {
    }

    public record ParsedColumn(
            int ordinalPosition,
            String columnName,
            String normalizedName,
            String inferredType,
            boolean nullable,
            List<String> sampleValues
    ) {
    }
}
