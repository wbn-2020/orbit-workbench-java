package com.orbitworkbench.dataset.application;

import com.orbitworkbench.dataset.domain.DatasetColumnRecord;
import com.orbitworkbench.dataset.domain.DatasetRecord;
import com.orbitworkbench.dataset.domain.DatasetSheetRecord;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.shared.config.DatasetProperties;
import com.orbitworkbench.storage.application.LocalStorageService;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.channels.Channels;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

@Service
public class DatasetRowScanService {

    private final LocalStorageService storageService;
    private final DatasetProperties properties;

    public DatasetRowScanService(LocalStorageService storageService,
                                 DatasetProperties properties) {
        this.storageService = storageService;
        this.properties = properties;
    }

    public void scan(DatasetRecord dataset,
                     DatasetSheetRecord sheet,
                     List<DatasetColumnRecord> columns,
                     RowConsumer consumer) {
        try (InputStream input = storageService.open(dataset.getDocumentStorageRef())) {
            if ("CSV".equals(dataset.getFormat())) {
                if (sheet.getSheetIndex() != 0) {
                    throw invalid("CSV 只包含一个工作表");
                }
                scanCsv(input, columns.size(), consumer);
                return;
            }
            if ("XLSX".equals(dataset.getFormat())) {
                scanXlsx(input, sheet.getSheetIndex(), columns.size(), consumer);
                return;
            }
            throw invalid("数据集格式不受支持");
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.DATASET_PARSE_FAILED,
                    "数据集无法重新扫描");
        }
    }

    private void scanCsv(InputStream input,
                         int columnCount,
                         RowConsumer consumer) throws IOException {
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
            boolean header = true;
            long rowNumber = 0;
            for (CSVRecord record : parser) {
                if (header) {
                    header = false;
                    continue;
                }
                checkInterrupted();
                rowNumber++;
                if (rowNumber > properties.getMaxRows()) {
                    throw invalid("数据行数超过限制");
                }
                List<String> row = new ArrayList<>(columnCount);
                for (int index = 0; index < columnCount; index++) {
                    row.add(index < record.size() ? record.get(index) : "");
                }
                consumer.accept(row);
            }
        }
    }

    private void scanXlsx(InputStream input,
                          int targetSheet,
                          int columnCount,
                          RowConsumer consumer) throws Exception {
        try (OPCPackage pkg = OPCPackage.open(input)) {
            ReadOnlySharedStringsTable strings = new ReadOnlySharedStringsTable(pkg);
            XSSFReader reader = new XSSFReader(pkg);
            StylesTable styles = reader.getStylesTable();
            XSSFReader.SheetIterator sheets =
                    (XSSFReader.SheetIterator) reader.getSheetsData();
            int index = 0;
            while (sheets.hasNext()) {
                try (InputStream sheetInput = sheets.next()) {
                    if (index == targetSheet) {
                        XlsxRowCollector collector =
                                new XlsxRowCollector(columnCount, consumer);
                        XMLReader xmlReader = secureXmlReader();
                        xmlReader.setContentHandler(new XSSFSheetXMLHandler(
                                styles,
                                null,
                                strings,
                                collector,
                                new DataFormatter(java.util.Locale.ROOT),
                                false));
                        xmlReader.parse(new InputSource(sheetInput));
                        return;
                    }
                }
                index++;
            }
        }
        throw invalid("数据集工作表不存在");
    }

    private void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new ApiException(
                    HttpStatus.REQUEST_TIMEOUT,
                    ErrorCode.CANCELLED,
                    "数据扫描已取消");
        }
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

    private ApiException invalid(String message) {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                ErrorCode.TOOL_CALL_INVALID,
                message);
    }

    @FunctionalInterface
    public interface RowConsumer {
        void accept(List<String> row);
    }

    private final class XlsxRowCollector
            implements XSSFSheetXMLHandler.SheetContentsHandler {

        private final int columnCount;
        private final RowConsumer consumer;
        private final Map<Integer, String> current = new HashMap<>();
        private boolean header = true;
        private long rowNumber;

        private XlsxRowCollector(int columnCount, RowConsumer consumer) {
            this.columnCount = columnCount;
            this.consumer = consumer;
        }

        @Override
        public void startRow(int rowNum) {
            current.clear();
        }

        @Override
        public void endRow(int rowNum) {
            if (header) {
                header = false;
                return;
            }
            checkInterrupted();
            rowNumber++;
            if (rowNumber > properties.getMaxRows()) {
                throw invalid("数据行数超过限制");
            }
            List<String> row = new ArrayList<>(columnCount);
            for (int index = 0; index < columnCount; index++) {
                row.add(current.getOrDefault(index, ""));
            }
            consumer.accept(row);
        }

        @Override
        public void cell(String cellReference,
                         String formattedValue,
                         XSSFComment comment) {
            int column = cellReference == null
                    ? current.size()
                    : new CellReference(cellReference).getCol();
            if (column < columnCount) {
                current.put(column, formattedValue == null ? "" : formattedValue);
            }
        }
    }
}
