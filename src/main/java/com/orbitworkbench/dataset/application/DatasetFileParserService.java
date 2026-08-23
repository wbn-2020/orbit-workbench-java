package com.orbitworkbench.dataset.application;

import com.orbitworkbench.dataset.application.DatasetParseResult.ParsedColumn;
import com.orbitworkbench.dataset.application.DatasetParseResult.ParsedSheet;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.shared.config.DatasetProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.channels.Channels;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParserFactory;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.springframework.stereotype.Service;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

@Service
public class DatasetFileParserService {

    private static final Pattern INTEGER = Pattern.compile("[-+]?\\d+");
    private static final Pattern DECIMAL =
            Pattern.compile("[-+]?(?:\\d+\\.\\d+|\\d+[eE][-+]?\\d+|\\d+\\.\\d*[eE][-+]?\\d+)");
    private static final int DISTINCT_LIMIT = 10_000;
    private static final int SAMPLE_LIMIT = 5;

    private final DatasetProperties properties;

    public DatasetFileParserService(DatasetProperties properties) {
        this.properties = properties;
    }

    public DatasetParseResult parse(String format, InputStream input) {
        Deadline deadline = new Deadline(properties.getParseTimeout());
        try {
            if ("CSV".equals(format)) {
                return parseCsv(input, deadline);
            }
            if ("XLSX".equals(format)) {
                return parseXlsx(input, deadline);
            }
            throw new DatasetParseException(
                    ErrorCode.DATASET_UNSUPPORTED_FORMAT,
                    "不支持的数据集格式");
        } catch (DatasetParseException exception) {
            throw exception;
        } catch (CharacterCodingException exception) {
            throw new DatasetParseException(
                    ErrorCode.DATASET_PARSE_FAILED,
                    "CSV 不是有效的 UTF-8 文本",
                    exception);
        } catch (Exception exception) {
            throw new DatasetParseException(
                    ErrorCode.DATASET_PARSE_FAILED,
                    "数据文件无法解析",
                    exception);
        }
    }

    private DatasetParseResult parseCsv(InputStream input,
                                        Deadline deadline) throws IOException {
        Reader reader = Channels.newReader(
                Channels.newChannel(input),
                StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT),
                -1);
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setIgnoreEmptyLines(false)
                .setTrim(false)
                .build();
        try (CSVParser parser = new CSVParser(reader, format)) {
            List<String> headers = null;
            SheetAccumulator sheet = null;
            for (CSVRecord record : parser) {
                deadline.check();
                List<String> row = new ArrayList<>(record.size());
                for (String value : record) {
                    row.add(validCell(value, deadline));
                }
                if (headers == null) {
                    headers = normalizeHeaders(row, deadline);
                    sheet = new SheetAccumulator(0, "CSV", headers, deadline);
                    continue;
                }
                sheet.accept(row);
            }
            if (headers == null || sheet == null) {
                throw new DatasetParseException(
                        ErrorCode.DATASET_PARSE_FAILED,
                        "CSV 文件为空");
            }
            return new DatasetParseResult(List.of(sheet.finish()));
        }
    }

    private DatasetParseResult parseXlsx(InputStream input,
                                         Deadline deadline) throws Exception {
        List<ParsedSheet> result = new ArrayList<>();
        try (OPCPackage pkg = OPCPackage.open(input)) {
            ReadOnlySharedStringsTable strings = new ReadOnlySharedStringsTable(pkg);
            XSSFReader reader = new XSSFReader(pkg);
            StylesTable styles = reader.getStylesTable();
            XSSFReader.SheetIterator sheets =
                    (XSSFReader.SheetIterator) reader.getSheetsData();
            int index = 0;
            while (sheets.hasNext()) {
                deadline.check();
                if (index >= properties.getMaxSheets()) {
                    throw new DatasetParseException(
                            ErrorCode.DATASET_SHEET_LIMIT_EXCEEDED,
                            "XLSX 工作表数量超过限制");
                }
                try (InputStream sheetInput = sheets.next()) {
                    XlsxSheetCollector collector =
                            new XlsxSheetCollector(
                                    index, sheets.getSheetName(), deadline);
                    XMLReader xmlReader = secureXmlReader();
                    xmlReader.setContentHandler(new XSSFSheetXMLHandler(
                            styles,
                            null,
                            strings,
                            collector,
                            new DataFormatter(Locale.ROOT),
                            false));
                    xmlReader.parse(new InputSource(sheetInput));
                    result.add(collector.finish());
                }
                index++;
            }
        }
        if (result.isEmpty()) {
            throw new DatasetParseException(
                    ErrorCode.DATASET_PARSE_FAILED,
                    "XLSX 不包含可读取的工作表");
        }
        return new DatasetParseResult(result);
    }

    private String validCell(String value, Deadline deadline) {
        deadline.check();
        String normalized = value == null ? "" : value;
        if (normalized.length() > properties.getMaxCellCharacters()) {
            throw new DatasetParseException(
                    ErrorCode.DATASET_COLUMN_LIMIT_EXCEEDED,
                    "单元格内容超过允许长度");
        }
        return normalized;
    }

    private List<String> normalizeHeaders(List<String> rawHeaders,
                                          Deadline deadline) {
        deadline.check();
        if (rawHeaders.size() > properties.getMaxColumns()) {
            throw new DatasetParseException(
                    ErrorCode.DATASET_COLUMN_LIMIT_EXCEEDED,
                    "数据字段数量超过限制");
        }
        if (rawHeaders.isEmpty()) {
            throw new DatasetParseException(
                    ErrorCode.DATASET_PARSE_FAILED,
                    "数据文件缺少表头");
        }
        List<String> headers = new ArrayList<>(rawHeaders.size());
        Set<String> used = new HashSet<>();
        for (int index = 0; index < rawHeaders.size(); index++) {
            String header = rawHeaders.get(index) == null
                    ? "" : rawHeaders.get(index).trim();
            if (index == 0 && header.startsWith("\uFEFF")) {
                header = header.substring(1).trim();
            }
            if (header.isEmpty()) {
                header = "column_" + (index + 1);
            }
            String candidate = header;
            int suffix = 2;
            while (!used.add(candidate.toLowerCase(Locale.ROOT))) {
                candidate = header + "_" + suffix++;
            }
            headers.add(candidate);
        }
        return headers;
    }

    private final class XlsxSheetCollector
            implements XSSFSheetXMLHandler.SheetContentsHandler {

        private final int sheetIndex;
        private final String sheetName;
        private final Deadline deadline;
        private final Map<Integer, String> current = new HashMap<>();
        private SheetAccumulator accumulator;

        private XlsxSheetCollector(int sheetIndex,
                                   String sheetName,
                                   Deadline deadline) {
            this.sheetIndex = sheetIndex;
            this.sheetName = sheetName == null || sheetName.isBlank()
                    ? "Sheet" + (sheetIndex + 1)
                    : sheetName;
            this.deadline = deadline;
        }

        @Override
        public void startRow(int rowNum) {
            current.clear();
        }

        @Override
        public void endRow(int rowNum) {
            deadline.check();
            if (current.isEmpty() && accumulator == null) {
                return;
            }
            int width = current.keySet().stream().mapToInt(Integer::intValue)
                    .max().orElse(-1) + 1;
            if (accumulator != null) {
                width = Math.max(width, accumulator.columnCount());
            }
            if (width > properties.getMaxColumns()) {
                throw new DatasetParseException(
                        ErrorCode.DATASET_COLUMN_LIMIT_EXCEEDED,
                        "XLSX 字段数量超过限制");
            }
            List<String> row = new ArrayList<>(width);
            for (int index = 0; index < width; index++) {
                row.add(current.getOrDefault(index, ""));
            }
            if (accumulator == null) {
                accumulator = new SheetAccumulator(
                        sheetIndex,
                        sheetName,
                        normalizeHeaders(row, deadline),
                        deadline);
            } else {
                accumulator.accept(row);
            }
        }

        @Override
        public void cell(String cellReference,
                         String formattedValue,
                         XSSFComment comment) {
            deadline.check();
            int column = cellReference == null
                    ? current.size()
                    : new CellReference(cellReference).getCol();
            if (column >= properties.getMaxColumns()) {
                throw new DatasetParseException(
                        ErrorCode.DATASET_COLUMN_LIMIT_EXCEEDED,
                        "XLSX 字段数量超过限制");
            }
            current.put(column, validCell(formattedValue, deadline));
        }

        private ParsedSheet finish() {
            if (accumulator == null) {
                accumulator = new SheetAccumulator(
                        sheetIndex,
                        sheetName,
                        List.of("column_1"),
                        deadline);
            }
            return accumulator.finish();
        }
    }

    private final class SheetAccumulator {

        private final int sheetIndex;
        private final String sheetName;
        private final List<String> headers;
        private final List<ColumnProfiler> profilers;
        private final List<List<String>> previewRows = new ArrayList<>();
        private final Set<Long> rowFingerprints = new HashSet<>();
        private final Deadline deadline;
        private long rowCount;
        private long duplicateRows;

        private SheetAccumulator(int sheetIndex,
                                 String sheetName,
                                 List<String> headers,
                                 Deadline deadline) {
            this.sheetIndex = sheetIndex;
            this.sheetName = sheetName;
            this.headers = headers;
            this.deadline = deadline;
            this.profilers = new ArrayList<>(headers.size());
            for (int index = 0; index < headers.size(); index++) {
                profilers.add(new ColumnProfiler(headers.get(index)));
            }
        }

        private int columnCount() {
            return headers.size();
        }

        private void accept(List<String> rawRow) {
            deadline.check();
            rowCount++;
            if (rowCount > properties.getMaxRows()) {
                throw new DatasetParseException(
                        ErrorCode.DATASET_ROW_LIMIT_EXCEEDED,
                        "数据行数超过限制");
            }
            if (rawRow.size() > headers.size()) {
                throw new DatasetParseException(
                        ErrorCode.DATASET_COLUMN_LIMIT_EXCEEDED,
                        "数据行字段数超过表头字段数");
            }
            List<String> row = new ArrayList<>(headers.size());
            long fingerprint = 0xcbf29ce484222325L;
            for (int index = 0; index < headers.size(); index++) {
                String value = index < rawRow.size()
                        ? validCell(rawRow.get(index), deadline)
                        : "";
                row.add(value);
                profilers.get(index).accept(value);
                fingerprint ^= value.hashCode();
                fingerprint *= 0x100000001b3L;
            }
            if (!rowFingerprints.add(fingerprint)) {
                duplicateRows++;
            }
            if (previewRows.size() < properties.getMaxPreviewRows()) {
                previewRows.add(List.copyOf(row));
            }
        }

        private ParsedSheet finish() {
            List<ParsedColumn> columns = new ArrayList<>(headers.size());
            List<Map<String, Object>> columnSummaries = new ArrayList<>(headers.size());
            long missingCells = 0;
            Set<String> normalizedNames = new LinkedHashSet<>();
            for (int index = 0; index < headers.size(); index++) {
                ColumnProfiler profiler = profilers.get(index);
                String normalizedName = normalizedName(headers.get(index), index, normalizedNames);
                columns.add(new ParsedColumn(
                        index,
                        headers.get(index),
                        normalizedName,
                        profiler.inferredType(),
                        profiler.nullCount > 0,
                        List.copyOf(profiler.samples)));
                columnSummaries.add(profiler.summary(index, headers.get(index)));
                missingCells += profiler.nullCount;
            }
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("rowCount", rowCount);
            summary.put("columnCount", headers.size());
            summary.put("columns", columnSummaries);

            Map<String, Object> quality = new LinkedHashMap<>();
            quality.put("missingCellCount", missingCells);
            quality.put("duplicateRowCount", duplicateRows);
            quality.put("hasTruncatedDistinctCounts",
                    profilers.stream().anyMatch(ColumnProfiler::distinctTruncated));
            return new ParsedSheet(
                    sheetIndex,
                    sheetName,
                    rowCount,
                    columns,
                    List.copyOf(previewRows),
                    summary,
                    quality);
        }
    }

    private static final class ColumnProfiler {

        private final String name;
        private final Set<String> distinct = new HashSet<>();
        private final List<String> samples = new ArrayList<>();
        private String type = "EMPTY";
        private long nullCount;
        private long nonNullCount;
        private boolean distinctTruncated;
        private BigDecimal numericSum = BigDecimal.ZERO;
        private BigDecimal numericMin;
        private BigDecimal numericMax;
        private long numericCount;

        private ColumnProfiler(String name) {
            this.name = name;
        }

        private void accept(String raw) {
            String value = raw == null ? "" : raw.trim();
            if (value.isEmpty()) {
                nullCount++;
                return;
            }
            nonNullCount++;
            if (samples.size() < SAMPLE_LIMIT && !samples.contains(value)) {
                samples.add(value);
            }
            if (distinct.size() < DISTINCT_LIMIT) {
                distinct.add(value);
            } else if (!distinct.contains(value)) {
                distinctTruncated = true;
            }
            String valueType = detectType(value);
            type = mergeTypes(type, valueType);
            if ("INTEGER".equals(valueType) || "DECIMAL".equals(valueType)) {
                try {
                    BigDecimal numeric = new BigDecimal(value);
                    numericSum = numericSum.add(numeric);
                    numericMin = numericMin == null ? numeric : numericMin.min(numeric);
                    numericMax = numericMax == null ? numeric : numericMax.max(numeric);
                    numericCount++;
                } catch (NumberFormatException ignored) {
                    // Type detection already degrades mixed columns to STRING.
                }
            }
        }

        private String inferredType() {
            return type;
        }

        private boolean distinctTruncated() {
            return distinctTruncated;
        }

        private Map<String, Object> summary(int ordinal, String columnName) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ordinalPosition", ordinal);
            result.put("name", columnName);
            result.put("inferredType", type);
            result.put("nullCount", nullCount);
            result.put("nonNullCount", nonNullCount);
            result.put("distinctCount", distinct.size());
            result.put("distinctCountTruncated", distinctTruncated);
            if (numericCount > 0 && !"STRING".equals(type)) {
                result.put("min", numericMin);
                result.put("max", numericMax);
                result.put("average", numericSum.divide(
                        BigDecimal.valueOf(numericCount), 8, RoundingMode.HALF_UP));
            }
            return result;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static String detectType(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        if ("true".equals(lower) || "false".equals(lower)
                || "yes".equals(lower) || "no".equals(lower)) {
            return "BOOLEAN";
        }
        if (INTEGER.matcher(value).matches()) {
            return "INTEGER";
        }
        if (DECIMAL.matcher(value).matches()) {
            try {
                new BigDecimal(value);
                return "DECIMAL";
            } catch (NumberFormatException ignored) {
                return "STRING";
            }
        }
        if (isDateTime(value)) {
            return "DATETIME";
        }
        return "STRING";
    }

    private static String mergeTypes(String current, String incoming) {
        if ("EMPTY".equals(current)) {
            return incoming;
        }
        if (current.equals(incoming)) {
            return current;
        }
        if (("INTEGER".equals(current) && "DECIMAL".equals(incoming))
                || ("DECIMAL".equals(current) && "INTEGER".equals(incoming))) {
            return "DECIMAL";
        }
        return "STRING";
    }

    private static boolean isDateTime(String value) {
        try {
            Instant.parse(value);
            return true;
        } catch (RuntimeException ignored) {
        }
        try {
            OffsetDateTime.parse(value);
            return true;
        } catch (RuntimeException ignored) {
        }
        try {
            LocalDateTime.parse(value);
            return true;
        } catch (RuntimeException ignored) {
        }
        try {
            LocalDate.parse(value);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static String normalizedName(String name,
                                         int ordinal,
                                         Set<String> used) {
        String normalized = name.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", "_")
                .replaceAll("^_+|_+$", "");
        if (normalized.isBlank()) {
            normalized = "column_" + (ordinal + 1);
        }
        String candidate = normalized;
        int suffix = 2;
        while (!used.add(candidate)) {
            candidate = normalized + "_" + suffix++;
        }
        return candidate;
    }

    private XMLReader secureXmlReader() throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature(
                "http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature(
                "http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature(
                "http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature(
                "http://apache.org/xml/features/nonvalidating/load-external-dtd",
                false);
        XMLReader reader = factory.newSAXParser().getXMLReader();
        reader.setFeature(
                "http://xml.org/sax/features/external-general-entities", false);
        reader.setFeature(
                "http://xml.org/sax/features/external-parameter-entities", false);
        return reader;
    }

    private static final class Deadline {
        private final long deadlineNanos;

        private Deadline(Duration timeout) {
            Duration normalized = timeout == null
                    || timeout.isZero()
                    || timeout.isNegative()
                    ? Duration.ofMinutes(2)
                    : timeout;
            long now = System.nanoTime();
            long timeoutNanos;
            try {
                timeoutNanos = normalized.toNanos();
            } catch (ArithmeticException exception) {
                timeoutNanos = Long.MAX_VALUE;
            }
            this.deadlineNanos = timeoutNanos >= Long.MAX_VALUE - now
                    ? Long.MAX_VALUE
                    : now + timeoutNanos;
        }

        private void check() {
            if (Thread.currentThread().isInterrupted()
                    || System.nanoTime() > deadlineNanos) {
                throw new DatasetParseException(
                        ErrorCode.REQUEST_TIMEOUT,
                        "数据集解析超过时间限制");
            }
        }
    }
}
