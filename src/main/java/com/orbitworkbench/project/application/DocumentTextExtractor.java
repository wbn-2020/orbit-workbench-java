package com.orbitworkbench.project.application;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

/**
 * PDF/DOCX/PPTX/XLSX 文本提取（V-002 第一版）。
 * 只做只读文本提取，不执行文档内脚本/宏/外部链接；二进制内容不进入存储。
 */
@Component
public class DocumentTextExtractor {

    private static final int MAX_TEXT_CHARS = 200000;

    public boolean supports(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase();
        return lower.endsWith(".pdf") || lower.endsWith(".docx")
                || lower.endsWith(".pptx") || lower.endsWith(".xlsx");
    }

    public String mediaType(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase();
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (lower.endsWith(".pptx")) return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    }

    /**
     * 提取文本；提取失败抛 IOException 由调用方决定该文件记为 FAILED。
     */
    public String extract(String fileName, InputStream input) throws IOException {
        String lower = fileName.toLowerCase();
        byte[] bytes = input.readAllBytes();
        String text = switch (lower.endsWith(".pdf") ? "pdf"
                : lower.endsWith(".docx") ? "docx"
                : lower.endsWith(".pptx") ? "pptx" : "xlsx") {
            case "pdf" -> extractPdf(bytes);
            case "docx" -> extractDocx(bytes);
            case "pptx" -> extractPptx(bytes);
            default -> extractXlsx(bytes);
        };
        if (text != null && text.length() > MAX_TEXT_CHARS) {
            text = text.substring(0, MAX_TEXT_CHARS);
        }
        return text == null || text.isBlank() ? null : text;
    }

    public InputStream toStream(String text) {
        return new ByteArrayInputStream(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String extractPdf(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private String extractDocx(byte[] bytes) throws IOException {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String extractPptx(byte[] bytes) throws IOException {
        try (XMLSlideShow slideshow = new XMLSlideShow(new ByteArrayInputStream(bytes))) {
            StringBuilder builder = new StringBuilder();
            slideshow.getSlides().forEach(slide -> {
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        appendParagraphs(builder, textShape.getTextParagraphs());
                    }
                }
            });
            return builder.toString();
        }
    }

    private String extractXlsx(byte[] bytes) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < workbook.getNumberOfSheets(); i += 1) {
                XSSFSheet sheet = workbook.getSheetAt(i);
                builder.append("## Sheet: ").append(sheet.getSheetName()).append('\n');
                for (var row : sheet) {
                    List<String> cells = new ArrayList<>();
                    row.forEach(cell -> {
                        String value = cell.toString();
                        if (value != null && !value.isBlank()) {
                            cells.add(value.trim());
                        }
                    });
                    if (!cells.isEmpty()) {
                        builder.append(String.join(" | ", cells)).append('\n');
                    }
                }
                builder.append('\n');
            }
            return builder.toString();
        }
    }

    private void appendParagraphs(StringBuilder builder,
                                  List<XSLFTextParagraph> paragraphs) {
        for (XSLFTextParagraph paragraph : paragraphs) {
            StringBuilder line = new StringBuilder();
            for (XSLFTextRun run : paragraph.getTextRuns()) {
                line.append(run.getRawText());
            }
            if (!line.isEmpty()) {
                builder.append(line).append('\n');
            }
        }
    }
}
