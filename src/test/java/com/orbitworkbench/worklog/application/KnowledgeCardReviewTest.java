package com.orbitworkbench.worklog.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.worklog.domain.KnowledgeCardRow;
import com.orbitworkbench.worklog.infrastructure.mapper.KnowledgeCardMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KnowledgeCardReviewTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-09-09");

    @Mock
    private KnowledgeCardMapper mapper;

    private KnowledgeCardService service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeCardService(mapper, new com.fasterxml.jackson.databind.ObjectMapper());
    }

    private KnowledgeCardRow row(Integer stage, LocalDate nextDate) {
        KnowledgeCardRow row = new KnowledgeCardRow();
        row.setId(7L);
        row.setTitle("库存分桶");
        row.setSummary("摘要");
        row.setTagsJson("[\"库存\"]");
        row.setReviewStage(stage);
        row.setNextReviewDate(nextDate);
        row.setCreatedAt(Instant.parse("2026-09-01T00:00:00Z"));
        return row;
    }

    @Test
    void firstReviewEntersStageOneDueTomorrow() {
        when(mapper.findOwned(1L, 7L)).thenReturn(row(null, null));

        var response = service.review(1L, 7L, TODAY);

        assertEquals("7", response.id());
        assertEquals(1, response.reviewStage());
        assertEquals(TODAY.plusDays(1).toString(), response.nextReviewDate());
        verify(mapper).markReviewed(eq(7L), eq(1L), eq(1),
                eq(TODAY.plusDays(1)), any(Instant.class));
    }

    @Test
    void reviewAdvancesThroughLadderToFourteenDayCap() {
        // stage 3 → 4：间隔 14 天
        when(mapper.findOwned(1L, 7L)).thenReturn(row(3, TODAY.minusDays(7)));
        var response = service.review(1L, 7L, TODAY);
        assertEquals(4, response.reviewStage());
        assertEquals(TODAY.plusDays(14).toString(), response.nextReviewDate());

        // stage 4 再回顾：封顶仍 14 天
        when(mapper.findOwned(1L, 7L)).thenReturn(row(4, TODAY.minusDays(14)));
        var capped = service.review(1L, 7L, TODAY);
        assertEquals(4, capped.reviewStage());
        assertEquals(TODAY.plusDays(14).toString(), capped.nextReviewDate());
    }

    @Test
    void reviewReturns404ForForeignCard() {
        when(mapper.findOwned(1L, 7L)).thenReturn(null);
        ApiException exception = assertThrows(ApiException.class,
                () -> service.review(1L, 7L, TODAY));
        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, exception.getStatus());
        verify(mapper, org.mockito.Mockito.never()).markReviewed(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(), any(), any());
    }

    @Test
    void dueCardsReturnOverdueDaysAndParsedTags() {
        KnowledgeCardRow row = row(2, TODAY.minusDays(3));
        when(mapper.listDue(1L, TODAY, 50)).thenReturn(List.of(row));

        var response = service.dueCards(1L, TODAY);

        assertEquals(1, response.cards().size());
        assertEquals(3, response.cards().get(0).overdueDays());
        assertEquals(2, response.cards().get(0).reviewStage());
        assertEquals(List.of("库存"), response.cards().get(0).tags());
    }
}
