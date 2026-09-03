package com.orbitworkbench.resume.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.resume.api.ResumeDtos.ItemPayload;
import com.orbitworkbench.resume.api.ResumeDtos.SectionPayload;
import com.orbitworkbench.resume.api.ResumeDtos.SourcePayload;
import com.orbitworkbench.shared.api.ApiException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResumeSectionsTest {

    @Test
    void keepsProjectLocatorWhenNormalizingProjectFactSource() {
        SourcePayload source = new SourcePayload(
                "PROJECT_FACT", 42L, "项目 · 版本 3 · 已确认事实", 7L, 21L);
        ItemPayload item = new ItemPayload(
                "item-1", 1, "ENTRY", "库存链路", "负责幂等与防超卖", source, false);

        ResumeSections.Model model = ResumeSections.normalize(List.of(
                new SectionPayload("PROJECT_EXPERIENCE", List.of(item))));

        ResumeSections.Source normalized = model.itemsOf(
                com.orbitworkbench.resume.domain.ResumeSectionKey.PROJECT_EXPERIENCE)
                .getFirst().source();
        assertEquals(42L, normalized.refId());
        assertEquals(7L, normalized.projectId());
        assertEquals(21L, normalized.projectVersionId());
    }

    @Test
    void readsLegacySourceWithoutProjectLocator() {
        String legacyJson = """
                {
                  "schemaVersion": 1,
                  "sections": [{
                    "key": "PROJECT_EXPERIENCE",
                    "items": [{
                      "id": "item-1",
                      "order": 1,
                      "kind": "ENTRY",
                      "label": "库存链路",
                      "text": "负责幂等与防超卖",
                      "source": {
                        "type": "PROJECT_FACT",
                        "refId": 42,
                        "label": "项目 · 版本 3 · 已确认事实"
                      },
                      "edited": false
                    }]
                  }]
                }
                """;

        ResumeSections.Model model = new ResumeSectionsJson(new ObjectMapper()).read(legacyJson);
        ResumeSections.Source source = model.sections().getFirst().items().getFirst().source();

        assertEquals(42L, source.refId());
        assertNull(source.projectId());
        assertNull(source.projectVersionId());
    }

    @Test
    void rejectsProjectLocatorOnManualSource() {
        SourcePayload source = new SourcePayload("MANUAL", null, null, 7L, 21L);
        ItemPayload item = new ItemPayload(
                "item-1", 1, "ENTRY", "库存链路", "负责幂等与防超卖", source, false);

        assertThrows(ApiException.class, () -> ResumeSections.normalize(List.of(
                new SectionPayload("PROJECT_EXPERIENCE", List.of(item)))));
    }
}
