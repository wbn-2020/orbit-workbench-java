package com.orbitworkbench.resume.application;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.orbitworkbench.resume.api.ResumeDtos.ItemPayload;
import com.orbitworkbench.resume.api.ResumeDtos.SectionPayload;
import com.orbitworkbench.resume.api.ResumeDtos.SourcePayload;
import com.orbitworkbench.resume.domain.ResumeItemKind;
import com.orbitworkbench.resume.domain.ResumeSectionKey;
import com.orbitworkbench.resume.domain.ResumeSourceType;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.shared.api.RequestEnums;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;

/**
 * sections_json 的唯一模型与校验入口（13 §5）。请求、存储和 PDF 渲染共用这一套结构，
 * 校验规则集中在这里，避免导出与保存两条路径分叉。
 */
public final class ResumeSections {

    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_ITEMS_PER_SECTION = 30;
    public static final int MAX_TOTAL_CHARS = 60000;
    public static final int MAX_JSON_BYTES = 512 * 1024;

    private ResumeSections() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Source(ResumeSourceType type, Long refId, String label) {
        public Source {
            if (type == null) {
                type = ResumeSourceType.MANUAL;
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(String id, int order, ResumeItemKind kind, String label, String text,
                       Source source, boolean edited) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Section(ResumeSectionKey key, List<Item> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Model(int schemaVersion, List<Section> sections) {

        public int itemCount() {
            return sections.stream().mapToInt(section -> section.items().size()).sum();
        }

        public int totalChars() {
            return sections.stream()
                    .flatMap(section -> section.items().stream())
                    .mapToInt(item -> item.text() == null ? 0 : item.text().length())
                    .sum();
        }

        public List<Item> itemsOf(ResumeSectionKey key) {
            return sections.stream()
                    .filter(section -> section.key() == key)
                    .findFirst()
                    .map(Section::items)
                    .orElse(List.of());
        }
    }

    /** 请求结构 → 归一化模型：区块按固定顺序补齐、order 重排、去空白。 */
    public static Model normalize(List<SectionPayload> payloads) {
        if (payloads == null || payloads.isEmpty()) {
            throw invalid("简历区块不能为空");
        }
        Set<String> ids = new HashSet<>();
        List<Section> normalized = new ArrayList<>();
        for (ResumeSectionKey key : ResumeSectionKey.values()) {
            normalized.add(new Section(key, new ArrayList<>()));
        }
        int totalChars = 0;
        for (SectionPayload payload : payloads) {
            ResumeSectionKey key = RequestEnums.parse(ResumeSectionKey.class, payload.key(), "sections[].key");
            Section target = normalized.stream()
                    .filter(section -> section.key() == key)
                    .findFirst().orElseThrow(() -> invalid("未知简历区块：" + payload.key()));
            if (!target.items().isEmpty()) {
                throw invalid("简历区块重复：" + payload.key());
            }
            int order = 1;
            for (ItemPayload item : payload.items() == null ? List.<ItemPayload>of() : payload.items()) {
                String id = item.id() == null ? "" : item.id().trim();
                if (id.isEmpty() || !ids.add(id)) {
                    throw invalid("条目 id 为空或重复");
                }
                ResumeItemKind kind = RequestEnums.parse(ResumeItemKind.class, item.kind(), "items[].kind");
                Source source = toSource(item.source());
                String text = item.text().trim();
                if (text.isEmpty()) {
                    throw invalid("条目正文不能为空：" + key.name());
                }
                totalChars += text.length();
                if (target.items().size() >= MAX_ITEMS_PER_SECTION) {
                    throw invalid("单区块最多 " + MAX_ITEMS_PER_SECTION + " 条：" + key.name());
                }
                target.items().add(new Item(id, order++, kind,
                        item.label() == null ? null : item.label().trim(), text, source,
                        Boolean.TRUE.equals(item.edited())));
            }
        }
        if (totalChars > MAX_TOTAL_CHARS) {
            throw invalid("简历正文总量超过 " + MAX_TOTAL_CHARS + " 字");
        }
        return new Model(SCHEMA_VERSION, List.copyOf(normalized));
    }

    private static Source toSource(SourcePayload payload) {
        if (payload == null) {
            return new Source(ResumeSourceType.MANUAL, null, null);
        }
        ResumeSourceType type = RequestEnums.parse(ResumeSourceType.class, payload.type(), "items[].source.type");
        if (type != ResumeSourceType.MANUAL && payload.refId() == null) {
            throw invalid("自动带入条目必须带来源 id：" + type.name());
        }
        if (type == ResumeSourceType.MANUAL && payload.refId() != null) {
            throw invalid("手工条目不得带来源 id");
        }
        return new Source(type, payload.refId(),
                payload.label() == null ? null : payload.label().trim());
    }

    public static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, message);
    }
}
