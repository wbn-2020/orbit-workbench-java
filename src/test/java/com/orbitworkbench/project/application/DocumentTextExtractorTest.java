package com.orbitworkbench.project.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/**
 * 用 POI/PDFBox 现场生成样本，验证四类办公文档都能被只读提取，
 * 以及空文档、损坏文档和超长文本的处理方式。
 */
class DocumentTextExtractorTest {

    private final DocumentTextExtractor extractor = new DocumentTextExtractor();

    @Test
    void extractsFourOfficeFormats() throws IOException {
        assertTrue(extractor.extract("说明.docx", docx("秒杀中台 峰值 10000 QPS")).contains("秒杀中台"));
        assertTrue(extractor.extract("清单.xlsx", xlsx("依赖版本")).contains("spring-boot"));
        assertTrue(extractor.extract("评审.pptx", pptx("架构评审")).contains("容量评估"));
        assertTrue(extractor.extract("spec.pdf", pdf("idempotency key design")).contains("idempotency"));
    }

    @Test
    void recognisesSupportedNamesRegardlessOfCase() {
        assertTrue(extractor.supports("SECURITY.PDF"));
        assertTrue(extractor.supports("Design.Docx"));
        assertTrue(extractor.supports("plan.PPTX"));
        assertTrue(extractor.supports("metrics.XLSX"));
        assertFalse(extractor.supports("notes.txt"));
        assertFalse(extractor.supports(null));
    }

    @Test
    void reportsMediaTypePerFormat() {
        assertEquals("application/pdf", extractor.mediaType("a.pdf"));
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                extractor.mediaType("a.docx"));
        assertEquals("application/vnd.openxmlformats-officedocument.presentationml.presentation",
                extractor.mediaType("a.pptx"));
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                extractor.mediaType("a.xlsx"));
    }

    @Test
    void blankDocumentYieldsNoText() throws IOException {
        assertNull(extractor.extract("empty.docx", docx("   ")));
        assertNull(extractor.extract("empty.pdf", pdf("")));
    }

    @Test
    void corruptDocumentPropagatesFailureSoCallerCanRecordFailed() {
        byte[] garbage = "this is definitely not an office document".getBytes();

        assertThrows(Exception.class,
                () -> extractor.extract("broken.docx", new ByteArrayInputStream(garbage)));
        assertThrows(Exception.class,
                () -> extractor.extract("broken.pdf", new ByteArrayInputStream(garbage)));
    }

    @Test
    void refusesAbsurdlyCompressibleDocumentAsZipBomb() {
        byte[] bomb;
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("数".repeat(250_000));
            document.write(out);
            bomb = out.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }

        assertThrows(Exception.class,
                () -> extractor.extract("bomb.docx", new ByteArrayInputStream(bomb)),
                "POI 的 zip 炸弹保护必须阻止解压式放大，调用方据此把文件记为 FAILED");
    }

    @Test
    void truncatesRunawayTextToColumnBudget() throws IOException {
        String text = extractor.extract("long.docx", variedDocx(320));

        assertEquals(200_000, text.length(), "超过上限必须截断，避免整篇文档进入提示词与检索索引");
    }

    private InputStream docx(String text) throws IOException {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (!text.isBlank()) {
                document.createParagraph().createRun().setText(text);
            }
            document.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    /**
     * 生成段落多但不可极度压缩的文档，用于验证字符上限而不是先撞上 zip 炸弹保护。
     */
    private InputStream variedDocx(int paragraphs) throws IOException {
        java.util.Random random = new java.util.Random(20260831L);
        String alphabet = "abcdefghijkmnpqrstuvwxyz0123456789ABCDEFGHJKLMNPQRSTUVWXYZ";
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int index = 0; index < paragraphs; index += 1) {
                StringBuilder paragraph = new StringBuilder("段落" + index + " ");
                while (paragraph.length() < 800) {
                    paragraph.append(alphabet.charAt(random.nextInt(alphabet.length())));
                }
                document.createParagraph().createRun().setText(paragraph.toString());
            }
            document.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    private InputStream xlsx(String marker) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet("依赖");
            sheet.createRow(0).createCell(0).setCellValue(marker);
            sheet.createRow(1).createCell(0).setCellValue("spring-boot 3.5.16");
            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    private InputStream pptx(String title) throws IOException {
        try (XMLSlideShow show = new XMLSlideShow(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSLFSlide slide = show.createSlide();
            XSLFTextBox box = slide.createTextBox();
            box.setAnchor(new java.awt.geom.Rectangle2D.Double(50, 50, 600, 200));
            box.setText(title + "\n容量评估与降级预案");
            show.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }

    private InputStream pdf(String text) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            if (!text.isBlank()) {
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    stream.newLineAtOffset(60, 780);
                    stream.showText(text);
                    stream.endText();
                }
            }
            document.save(out);
            return new ByteArrayInputStream(out.toByteArray());
        }
    }
}
