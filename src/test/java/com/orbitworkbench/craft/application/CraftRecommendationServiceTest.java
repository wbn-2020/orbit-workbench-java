package com.orbitworkbench.craft.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.craft.api.CraftDtos.CraftRecommendationsResponse;
import com.orbitworkbench.craft.api.CraftDtos.WeaknessCraftRecommendation;
import com.orbitworkbench.craft.domain.CraftNoteRecord;
import com.orbitworkbench.craft.domain.CraftSource;
import com.orbitworkbench.craft.domain.CraftStatus;
import com.orbitworkbench.craft.infrastructure.mapper.CraftNoteMapper;
import com.orbitworkbench.interview.domain.ReportCenterRow;
import com.orbitworkbench.interview.infrastructure.mapper.InterviewReportMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CraftRecommendationServiceTest {

    @Mock private InterviewReportMapper reportMapper;
    @Mock private CraftNoteMapper craftMapper;

    private CraftRecommendationService service;

    @BeforeEach
    void setUp() {
        service = new CraftRecommendationService(reportMapper, craftMapper, new ObjectMapper());
    }

    private static ReportCenterRow readyReport(String dimsJson) {
        ReportCenterRow row = new ReportCenterRow();
        row.setReportId(11L);
        row.setSessionTitle("Java 后端二面");
        row.setReportStatus("REPORT_READY");
        row.setDimensionScoresJson(dimsJson);
        return row;
    }

    private static CraftNoteRecord craft(long id, String category, String title,
                                         String whenToUse, String content) {
        CraftNoteRecord r = new CraftNoteRecord();
        r.setId(id);
        r.setUserId(7L);
        r.setCategory(category);
        r.setTitle(title);
        r.setWhenToUse(whenToUse);
        r.setContent(content);
        r.setTagsJson("[]");
        r.setSource(CraftSource.USER_ENTERED);
        r.setConfirmationStatus(CraftStatus.CONFIRMED);
        return r;
    }

    @Test
    void noReportMeansHonestBasis() {
        when(reportMapper.listByUser(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of());

        CraftRecommendationsResponse response = service.recommend(7L);

        assertTrue(response.items().isEmpty());
        assertTrue(response.basis().contains("还没有出分的面试报告"));
    }

    @Test
    void allDimensionsStrongMeansNoWeakness() {
        when(reportMapper.listByUser(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(readyReport("{\"排障与异常恢复\": 85, \"表达结构\": 90}")));

        CraftRecommendationsResponse response = service.recommend(7L);

        assertTrue(response.items().isEmpty());
        assertTrue(response.basis().contains("暂无需要补的弱项"));
    }

    @Test
    void weakestDimensionMatchesKeywordCraft() {
        when(reportMapper.listByUser(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(readyReport(
                        "{\"排障与异常恢复\": 55, \"表达结构\": 88, \"架构取舍\": 66}")));
        when(craftMapper.listConfirmedForPrompt(7L)).thenReturn(List.of(
                craft(1L, "PLAYBOOK", "并发问题五步排查法", "排查线上并发异常时", "1. 定位入口线程"),
                craft(2L, "PLAYBOOK", "架构选型三问法", "做技术选型取舍时", "1. 列约束 2. 对比方案")));

        CraftRecommendationsResponse response = service.recommend(7L);

        assertEquals(2, response.items().size());
        WeaknessCraftRecommendation first = response.items().get(0);
        assertEquals("排障与异常恢复", first.dimension());
        assertEquals(55, first.score());
        assertEquals(1L, first.craftId()); // 「排查 / 异常」关键词命中
        WeaknessCraftRecommendation second = response.items().get(1);
        assertEquals("架构取舍", second.dimension());
        assertEquals(2L, second.craftId()); // 「架构 / 选型 / 取舍」命中
    }

    @Test
    void masteredCraftsAreNotRecommendedAgain() {
        when(reportMapper.listByUser(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(readyReport("{\"排障与异常恢复\": 55}")));
        CraftNoteRecord practiced = craft(1L, "PLAYBOOK", "并发问题五步排查法", "排查异常时", "排查步骤");
        practiced.setPracticeCount(1);
        when(craftMapper.listConfirmedForPrompt(7L)).thenReturn(List.of(practiced));

        CraftRecommendationsResponse response = service.recommend(7L);

        assertTrue(response.items().isEmpty());
        assertTrue(response.basis().contains("没有对得上的套路"));
    }

    @Test
    void oneCraftServesOnlyTheWeakestDimension() {
        when(reportMapper.listByUser(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(readyReport(
                        "{\"排障与异常恢复\": 50, \"问题分析\": 55, \"实现深度\": 60}")));
        // 只有一条既含「排查」又含「分析」「实现」的套路：它只能给最弱的那一个维度
        when(craftMapper.listConfirmedForPrompt(7L)).thenReturn(List.of(
                craft(9L, "PLAYBOOK", "线上事故定位与修复", "排查分析实现类问题", "先排查再分析最后验证实现")));

        CraftRecommendationsResponse response = service.recommend(7L);

        assertEquals(1, response.items().size());
        assertEquals("排障与异常恢复", response.items().get(0).dimension());
    }

    @Test
    void onlyReadyReportsCount() {
        ReportCenterRow failed = readyReport("{\"排障与异常恢复\": 20}");
        failed.setReportStatus("REPORT_FAILED");
        when(reportMapper.listByUser(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(failed));

        CraftRecommendationsResponse response = service.recommend(7L);

        assertTrue(response.basis().contains("还没有出分的面试报告"));
    }
}
