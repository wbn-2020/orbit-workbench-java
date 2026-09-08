package com.orbitworkbench.worklog.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.worklog.api.KnowledgeCardDtos.UpdateKnowledgeCardRequest;
import com.orbitworkbench.worklog.domain.KnowledgeCardRow;
import com.orbitworkbench.worklog.infrastructure.mapper.KnowledgeCardMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KnowledgeCardServiceTest {

    @Mock
    private KnowledgeCardMapper mapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private KnowledgeCardRow row() {
        KnowledgeCardRow row = new KnowledgeCardRow();
        row.setId(7L);
        row.setTitle("旧标题");
        row.setSummary("旧摘要");
        row.setSourceLogId(3L);
        row.setTagsJson("[\"数据库\"]");
        row.setCreatedAt(Instant.parse("2026-09-07T00:00:00Z"));
        return row;
    }

    @Test
    void updateWritesContentAndSerializesTags() {
        KnowledgeCardRow updated = row();
        updated.setTitle("新标题");
        updated.setSummary("新摘要");
        updated.setTagsJson("[\"数据库\",\"缓存\"]");
        when(mapper.findOwned(1L, 7L)).thenReturn(row(), updated);
        when(mapper.updateContent(eq(7L), eq(1L), eq("新标题"), eq("新摘要"), any(), any()))
                .thenReturn(1);
        KnowledgeCardService service = new KnowledgeCardService(mapper, objectMapper);

        var response = service.update(1L, 7L,
                new UpdateKnowledgeCardRequest("  新标题 ", " 新摘要 ", java.util.List.of("数据库", "缓存")));

        assertEquals("新标题", response.title());
        assertEquals(java.util.List.of("数据库", "缓存"), response.tags());
        ArgumentCaptor<String> tagsCaptor = ArgumentCaptor.forClass(String.class);
        verify(mapper).updateContent(eq(7L), eq(1L), eq("新标题"), eq("新摘要"),
                tagsCaptor.capture(), any());
        org.junit.jupiter.api.Assertions.assertTrue(tagsCaptor.getValue().contains("缓存"));
    }

    @Test
    void updateRejectsBlankTitleOrSummary() {
        when(mapper.findOwned(1L, 7L)).thenReturn(row());
        KnowledgeCardService service = new KnowledgeCardService(mapper, objectMapper);

        assertThrows(ApiException.class, () -> service.update(1L, 7L,
                new UpdateKnowledgeCardRequest("  ", "摘要", null)));
        assertThrows(ApiException.class, () -> service.update(1L, 7L,
                new UpdateKnowledgeCardRequest("标题", "  ", null)));
        verify(mapper, org.mockito.Mockito.never()).updateContent(anyLong(), anyLong(), any(), any(), any(), any());
    }

    @Test
    void updateReturns404ForForeignCard() {
        when(mapper.findOwned(1L, 7L)).thenReturn(null);
        KnowledgeCardService service = new KnowledgeCardService(mapper, objectMapper);

        ApiException exception = assertThrows(ApiException.class, () -> service.update(1L, 7L,
                new UpdateKnowledgeCardRequest("标题", "摘要", null)));

        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, exception.getStatus());
        verify(mapper, org.mockito.Mockito.never()).updateContent(anyLong(), anyLong(), any(), any(), any(), any());
    }
}
