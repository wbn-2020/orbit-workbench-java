package com.orbitworkbench.resume.application;

import com.orbitworkbench.resume.application.ResumeSections.Item;
import com.orbitworkbench.resume.application.ResumeSections.Model;
import com.orbitworkbench.resume.application.ResumeSections.Section;
import com.orbitworkbench.resume.api.ResumeDtos.PreflightResponse;
import com.orbitworkbench.resume.domain.ResumeItemKind;
import com.orbitworkbench.resume.domain.ResumeSectionKey;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 导出前校验（13 §7）。preflight 端点与真实导出共用本方法，二者不得分叉：
 * 校验只返回可读原因清单，由调用方决定是展示（preflight）还是拒绝（导出）。
 */
@Component
public class ResumeExportPreflight {

    private static final int MIN_TOTAL_CHARS = 200;

    public PreflightResponse evaluate(Model model) {
        List<String> blockers = new ArrayList<>();
        List<Item> basic = model.itemsOf(ResumeSectionKey.BASIC_INFO);
        List<Item> projects = model.itemsOf(ResumeSectionKey.PROJECT_EXPERIENCE);
        List<Item> works = model.itemsOf(ResumeSectionKey.WORK_EXPERIENCE);
        List<Item> skills = model.itemsOf(ResumeSectionKey.SKILLS);

        if (basic.stream().noneMatch(item -> matchesLabel(item, "姓名"))) {
            blockers.add("基本信息缺少姓名");
        }
        if (basic.stream().noneMatch(item -> matchesLabel(item, "联系方式")
                || matchesLabel(item, "手机") || matchesLabel(item, "邮箱"))) {
            blockers.add("基本信息缺少联系方式（手机或邮箱）");
        }
        if (projects.isEmpty() && works.isEmpty()) {
            blockers.add("项目经历与工作经历至少需要一条内容");
        }
        if (skills.isEmpty()) {
            blockers.add("技能标签至少需要一条内容");
        }
        if (model.totalChars() < MIN_TOTAL_CHARS) {
            blockers.add("正文总量少于 " + MIN_TOTAL_CHARS + " 字，导出会接近空白页");
        }
        return new PreflightResponse(blockers.isEmpty(), List.copyOf(blockers),
                model.totalChars(), model.itemCount());
    }

    /** PDF 渲染前的控制字符清洗：不回写存储内容（13 §7 第 7 条）。 */
    public static String sanitize(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder kept = new StringBuilder();
        text.codePoints().forEach(codePoint -> {
            boolean droppable = Character.getType(codePoint) == Character.FORMAT
                    || (Character.isISOControl(codePoint) && codePoint != '\n' && codePoint != '\r');
            if (!droppable) {
                kept.appendCodePoint(codePoint);
            }
        });
        List<String> lines = new ArrayList<>();
        for (String line : kept.toString().replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            String trimmed = line.strip();
            if (!trimmed.isEmpty()) {
                lines.add(trimmed);
            }
        }
        return String.join(" ", lines);
    }

    static List<Section> withSanitizedText(Model model) {
        List<Section> sections = new ArrayList<>();
        for (Section section : model.sections()) {
            List<Item> items = new ArrayList<>();
            for (Item item : section.items()) {
                items.add(new Item(item.id(), item.order(), item.kind(),
                        item.label() == null ? null : sanitize(item.label()).trim(),
                        sanitize(item.text()), item.source(), item.edited()));
            }
            sections.add(new Section(section.key(), items));
        }
        return sections;
    }

    private boolean matchesLabel(Item item, String keyword) {
        String label = item.label() == null ? "" : item.label();
        return label.contains(keyword) && !item.text().isBlank();
    }

    /** 供 bootstrap 与渲染复用：区块内是否为可渲染条目。 */
    public static boolean renderable(Item item) {
        return item.kind() != null && item.text() != null && !item.text().isBlank();
    }
}
