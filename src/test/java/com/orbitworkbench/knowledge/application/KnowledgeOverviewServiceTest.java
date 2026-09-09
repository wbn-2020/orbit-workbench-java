package com.orbitworkbench.knowledge.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.orbitworkbench.knowledge.domain.ProjectFactRecord;
import com.orbitworkbench.knowledge.infrastructure.mapper.KnowledgeChunkMapper;
import com.orbitworkbench.knowledge.infrastructure.mapper.ProjectFactMapper;
import com.orbitworkbench.worklog.domain.KnowledgeCardRow;
import com.orbitworkbench.worklog.infrastructure.mapper.KnowledgeCardMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KnowledgeOverviewServiceTest {

    @Mock
    private ProjectFactMapper factMapper;
    @Mock
    private KnowledgeCardMapper cardMapper;
    @Mock
    private KnowledgeChunkMapper chunkMapper;

    @InjectMocks
    private KnowledgeOverviewService service;

    private ProjectFactRecord fact(long id, String title) {
        ProjectFactRecord record = new ProjectFactRecord();
        record.setId(id);
        record.setTitle(title);
        record.setContent("内容 " + id);
        record.setCreatedAt(Instant.parse("2026-09-08T00:00:00Z"));
        return record;
    }

    private KnowledgeCardRow card(long id, String title) {
        KnowledgeCardRow row = new KnowledgeCardRow();
        row.setId(id);
        row.setTitle(title);
        row.setSummary("摘要 " + id);
        row.setCreatedAt(Instant.parse("2026-09-08T00:00:00Z"));
        return row;
    }

    @Test
    void overviewAggregatesCountsAndRecentEntries() {
        when(factMapper.countByStatus(1L, "CONFIRMED")).thenReturn(3L);
        when(factMapper.countByStatus(1L, "ANALYZED")).thenReturn(4L);
        when(factMapper.countByStatus(1L, "ARCHIVED")).thenReturn(1L);
        when(factMapper.recentByStatus(1L, "CONFIRMED", 5)).thenReturn(List.of(fact(9L, "秒杀链路")));
        when(cardMapper.countByUser(1L)).thenReturn(2L);
        when(cardMapper.listByUser(1L, 5, 0)).thenReturn(List.of(card(7L, "库存分桶")));
        when(chunkMapper.countByUser(1L)).thenReturn(12L);

        var overview = service.overview(1L);

        assertEquals(3, overview.projectFacts().confirmed());
        assertEquals(4, overview.projectFacts().analyzed());
        assertEquals(1, overview.projectFacts().archived());
        assertEquals(1, overview.projectFacts().recent().size());
        assertEquals("秒杀链路", overview.projectFacts().recent().get(0).title());
        assertEquals(2, overview.knowledgeCards().total());
        assertEquals("库存分桶", overview.knowledgeCards().recent().get(0).title());
        assertEquals(12, overview.projectChunks().total());
    }

    @Test
    void overviewReturnsZerosWhenEverythingEmpty() {
        when(factMapper.countByStatus(1L, "CONFIRMED")).thenReturn(0L);
        when(factMapper.countByStatus(1L, "ANALYZED")).thenReturn(0L);
        when(factMapper.countByStatus(1L, "ARCHIVED")).thenReturn(0L);
        when(factMapper.recentByStatus(1L, "CONFIRMED", 5)).thenReturn(List.of());
        when(cardMapper.countByUser(1L)).thenReturn(0L);
        when(cardMapper.listByUser(1L, 5, 0)).thenReturn(List.of());
        when(chunkMapper.countByUser(1L)).thenReturn(0L);

        var overview = service.overview(1L);

        assertEquals(0, overview.projectFacts().confirmed());
        assertEquals(0, overview.knowledgeCards().total());
        assertEquals(0, overview.projectChunks().total());
        assertEquals(0, overview.projectFacts().recent().size());
        assertEquals(0, overview.knowledgeCards().recent().size());
    }
}
