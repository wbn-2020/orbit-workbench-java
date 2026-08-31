package com.orbitworkbench.resume.application;

import com.orbitworkbench.resume.application.ResumeSections.Item;
import com.orbitworkbench.resume.application.ResumeSections.Model;
import com.orbitworkbench.resume.application.ResumeSections.Section;
import com.orbitworkbench.resume.domain.ResumeItemKind;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.fontbox.ttf.TrueTypeCollection;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 中文简历 PDF 渲染（13 §8，C-01a spike 口径）。只使用仓库既有 pdfbox，
 * 字体从运行机读取、不打包进产物；找不到中文字体时显式失败，绝不退回核心字体产出乱码。
 */
@Component
public class ResumePdfRenderer {

    private static final List<String> FONT_DIRECTORIES = List.of(
            "C:/Windows/Fonts", "/usr/share/fonts", "/usr/local/share/fonts",
            "/Library/Fonts", "/System/Library/Fonts", "~/.fonts");
    private static final List<String> PREFERRED_TTF = List.of(
            "simhei.ttf", "simkai.ttf", "NotoSansSC-VF.ttf", "notosanscjk-regular.ttf",
            "wqy-zenhei.ttc", "simsun.ttc", "msyh.ttc");

    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float MARGIN = 40f;
    private static final float TOP = PDRectangle.A4.getHeight() - 60f;
    private static final float BOTTOM = 60f;
    private static final float NAME_SIZE = 20f;
    private static final float HEADING_SIZE = 13f;
    private static final float BODY_SIZE = 11f;
    private static final float LINE_GAP = 1.55f;

    public record Rendered(byte[] content, String fontName) {
    }

    public Rendered render(String title, Model model) {
        // 字体集合必须在 document.save 之后才能关闭：子集化是保存时才读取字形的
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream();
             FontHolder holder = loadCjkFont(document)) {
            Painter painter = new Painter(document, holder.font());
            painter.drawTitle(title);
            for (Section section : model.sections()) {
                if (section.items().isEmpty()) {
                    continue;
                }
                painter.drawHeading(section.key().heading());
                for (Item item : section.items()) {
                    painter.drawItem(item);
                }
            }
            painter.close();
            document.save(output);
            return new Rendered(output.toByteArray(), holder.fontName());
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.UNKNOWN_PROVIDER_ERROR,
                    "简历 PDF 渲染失败");
        }
    }

    /** 运行机没有可用中文字体时的失败口径：501 + 可读原因（D-042）。 */
    private FontHolder loadCjkFont(PDDocument document) {
        for (String directory : FONT_DIRECTORIES) {
            File dir = new File(expandHome(directory));
            if (!dir.isDirectory()) {
                continue;
            }
            File[] files = dir.listFiles();
            if (files == null) {
                continue;
            }
            for (String preferred : PREFERRED_TTF) {
                for (File file : files) {
                    if (!file.getName().equalsIgnoreCase(preferred)) {
                        continue;
                    }
                    FontHolder holder = load(document, file);
                    if (holder != null) {
                        return holder;
                    }
                }
            }
        }
        throw new ApiException(HttpStatus.NOT_IMPLEMENTED, ErrorCode.UNSUPPORTED_CAPABILITY,
                "本机未找到可用中文字体，无法生成中文 PDF");
    }

    private FontHolder load(PDDocument document, File file) {
        String name = file.getName().toLowerCase();
        try {
            if (name.endsWith(".ttc")) {
                TrueTypeCollection collection = new TrueTypeCollection(file);
                try {
                    List<String> names = new ArrayList<>();
                    collection.processAllFonts(font -> names.add(font.getName()));
                    if (names.isEmpty()) {
                        return null;
                    }
                    // 字体集合必须按真实 PostScript 名取，不能猜带空格的显示名（C-01a 实测）
                    TrueTypeFont picked = collection.getFontByName(names.get(0));
                    if (picked == null) {
                        return null;
                    }
                    PDFont font = PDType0Font.load(document, picked, true);
                    TrueTypeCollection owned = collection;
                    collection = null;
                    return new FontHolder(font, font.getName(), owned);
                } finally {
                    if (collection != null) {
                        collection.close();
                    }
                }
            }
            PDFont font = PDType0Font.load(document,
                    new BufferedInputStream(Files.newInputStream(file.toPath())), true);
            return new FontHolder(font, font.getName(), null);
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    private static String expandHome(String path) {
        return path.startsWith("~") ? System.getProperty("user.home") + path.substring(1) : path;
    }

    private static final class FontHolder implements AutoCloseable {

        private final PDFont font;
        private final String fontName;
        private final TrueTypeCollection collection;

        FontHolder(PDFont font, String fontName, TrueTypeCollection collection) {
            this.font = font;
            this.fontName = fontName;
            this.collection = collection;
        }

        PDFont font() {
            return font;
        }

        String fontName() {
            return fontName;
        }

        @Override
        public void close() throws IOException {
            if (collection != null) {
                collection.close();
            }
        }
    }

    private final class Painter implements AutoCloseable {

        private final PDDocument document;
        private final PDFont font;
        private PDPageContentStream stream;
        private float cursor = TOP;

        Painter(PDDocument document, PDFont font) throws IOException {
            this.document = document;
            this.font = font;
            this.stream = newStream();
        }

        private PDPageContentStream newStream() throws IOException {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            cursor = TOP;
            return new PDPageContentStream(document, page);
        }

        private void ensureSpace(float needed) throws IOException {
            if (cursor - needed < BOTTOM) {
                stream.close();
                stream = newStream();
            }
        }

        private void drawLine(String text, float size, float left) throws IOException {
            ensureSpace(size * LINE_GAP);
            stream.beginText();
            stream.setFont(font, size);
            stream.newLineAtOffset(left, cursor - size);
            stream.showText(text);
            stream.endText();
            cursor -= size * LINE_GAP;
        }

        void drawTitle(String title) throws IOException {
            drawLine(ResumeExportPreflight.sanitize(title).isBlank()
                    ? "简历" : ResumeExportPreflight.sanitize(title), NAME_SIZE, MARGIN);
            cursor -= 6f;
        }

        void drawHeading(String heading) throws IOException {
            ensureSpace(HEADING_SIZE * LINE_GAP + 8f);
            drawLine(heading, HEADING_SIZE, MARGIN);
            cursor -= 2f;
        }

        void drawItem(Item item) throws IOException {
            String text = ResumeExportPreflight.sanitize(item.text());
            if (text.isBlank()) {
                return;
            }
            String label = item.label() == null ? "" : ResumeExportPreflight.sanitize(item.label());
            String body = item.kind() == ResumeItemKind.FIELD && !label.isBlank()
                    ? label + "：" + text : text;
            String heading = item.kind() == ResumeItemKind.ENTRY && !label.isBlank() ? label : null;
            if (heading != null) {
                drawLine(heading, BODY_SIZE + 1f, MARGIN);
            }
            for (String line : wrap(body)) {
                drawLine(line, BODY_SIZE, heading == null ? MARGIN : MARGIN + 12f);
            }
            cursor -= 3f;
        }

        private List<String> wrap(String text) throws IOException {
            List<String> lines = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            float maxWidth = PAGE_WIDTH - 2 * MARGIN - 12f;
            for (int index = 0; index < text.length(); index += 1) {
                String candidate = current.append(text.charAt(index)).toString();
                if (font.getStringWidth(candidate) / 1000f * BODY_SIZE > maxWidth) {
                    if (current.length() == 1) {
                        lines.add(candidate);
                        current.setLength(0);
                    } else {
                        current.deleteCharAt(current.length() - 1);
                        lines.add(current.toString());
                        current.setLength(0);
                        current.append(text.charAt(index));
                    }
                }
            }
            if (!current.isEmpty()) {
                lines.add(current.toString());
            }
            return lines;
        }

        @Override
        public void close() throws IOException {
            stream.close();
        }
    }

}
