package com.orbitworkbench.craft.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.craft.api.CraftDtos.CraftEffectResponse;
import com.orbitworkbench.craft.domain.CraftNoteRecord;
import com.orbitworkbench.craft.domain.CraftSource;
import com.orbitworkbench.craft.domain.CraftStatus;
import com.orbitworkbench.craft.infrastructure.mapper.CraftNoteMapper;
import com.orbitworkbench.interview.domain.ReportCenterRow;
import com.orbitworkbench.interview.infrastructure.mapper.InterviewReportMapper;
import java.time.Instant;
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
class CraftEffectServiceTest {

    @Mock private CraftNoteMapper craftMapper;
    @Mock private InterviewReportMapper reportMapper;

    private CraftEffectService service;

    @BeforeEach
    void setUp() {
        service = new CraftEffectService(craftMapper, reportMapper, new ObjectMapper());
    }

    private static final Instant PRACTICED_AT = Instant.parse("2026-09-10T00:00:00Z");

    private static Instant daysAround(int days) {
        return PRACTICED_AT.plus(java.time.Duration.ofDays(days));
    }

    private static CraftNoteRecord mastered(String title, String whenToUse, String content) {
        CraftNoteRecord r = new CraftNoteRecord();
        r.setId(5L);
        r.setUserId(7L);
        r.setCategory("PLAYBOOK");
        r.setTitle(title);
        r.setWhenToUse(whenToUse);
        r.setContent(content);
        r.setTagsJson("[]");
        r.setSource(CraftSource.USER_ENTERED);
        r.setConfirmationStatus(CraftStatus.CONFIRMED);
        r.setPracticeCount(1);
        r.setLastPracticedAt(PRACTICED_AT);
        return r;
    }

    private static ReportCenterRow report(long id, Instant generatedAt, String dimsJson) {
        ReportCenterRow row = new ReportCenterRow();
        row.setReportId(id);
        row.setSessionTitle("面试" + id);
        row.setReportStatus("REPORT_READY");
        row.setGeneratedAt(generatedAt);
        row.setDimensionScoresJson(dimsJson);
        return row;
    }

    @Test
    void notMasteredCraftHasNoEntry() {
        CraftNoteRecord pending = new CraftNoteRecord();
        pending.setId(6L);
        pending.setUserId(7L);
        pending.setTitle("还没练的套路");
        pending.setWhenToUse("排查异常时");
        pending.setContent("排查步骤");
        pending.setTagsJson("[]");
        pending.setSource(CraftSource.USER_ENTERED);
        pending.setConfirmationStatus(CraftStatus.CONFIRMED);
        pending.setPracticeCount(0);
        when(craftMapper.listByUser(7L)).thenReturn(List.of(pending));

        assertTrue(service.effects(7L).isEmpty());
    }

    @Test
    void improvedWhenAfterAvgClearlyHigher() {
        when(craftMapper.listByUser(7L)).thenReturn(List.of(
                mastered("并发异常恢复五步", "线上异常时", "1. 排查根因 2. 小步恢复 3. 验证")));
        when(reportMapper.listByUser(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(
                        report(1L, daysAround(-5), "{\"排障与异常恢复\": 50}"),
                        report(2L, daysAround(-2), "{\"排障与异常恢复\": 60}"),
                        report(3L, daysAround(1), "{\"排障与异常恢复\": 75}"),
                        report(4L, daysAround(3), "{\"排障与异常恢复\": 80}")));

        List<CraftEffectResponse> effects = service.effects(7L);

        assertEquals(1, effects.size());
        CraftEffectResponse effect = effects.get(0);
        assertEquals("排障与异常恢复", effect.dimension());
        assertEquals(2, effect.beforeCount());
        assertEquals(55, effect.beforeAvg());
        assertEquals(2, effect.afterCount());
        assertEquals(78, effect.afterAvg());
        assertEquals("IMPROVED", effect.status());
    }

    @Test
    void withinNoiseIsFlatNotImproved() {
        when(craftMapper.listByUser(7L)).thenReturn(List.of(
                mastered("并发异常恢复五步", "线上异常时", "排查根因，小步恢复，验证")));
        when(reportMapper.listByUser(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(
                        report(1L, daysAround(-1), "{\"排障与异常恢复\": 70}"),
                        report(2L, daysAround(1), "{\"排障与异常恢复\": 73}")));

        List<CraftEffectResponse> effects = service.effects(7L);

        assertEquals("FLAT", effects.get(0).status());
    }

    @Test
    void oneSidedSamplesAreInsufficientNotZero() {
        when(craftMapper.listByUser(7L)).thenReturn(List.of(
                mastered("并发异常恢复五步", "线上异常时", "排查根因，小步恢复，验证")));
        when(reportMapper.listByUser(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(
                        report(1L, daysAround(1), "{\"排障与异常恢复\": 70}")));

        List<CraftEffectResponse> effects = service.effects(7L);

        assertEquals("INSUFFICIENT", effects.get(0).status());
        assertEquals(0, effects.get(0).beforeCount());
        assertEquals(null, effects.get(0).beforeAvg());
    }

    @Test
    void declinedStatusSymmetric() {
        when(craftMapper.listByUser(7L)).thenReturn(List.of(
                mastered("并发异常恢复五步", "线上异常时", "排查根因，小步恢复，验证")));
        when(reportMapper.listByUser(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(
                        report(1L, daysAround(-1), "{\"排障与异常恢复\": 85}"),
                        report(2L, daysAround(1), "{\"排障与异常恢复\": 60}")));

        assertEquals("DECLINED", service.effects(7L).get(0).status());
    }

    @Test
    void noReportsOrDirtyDataYieldNoEntry() {
        when(craftMapper.listByUser(7L)).thenReturn(List.of(
                mastered("并发异常恢复五步", "线上异常时", "排查根因，小步恢复，验证")));
        when(reportMapper.listByUser(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(
                        report(1L, null, "{\"排障与异常恢复\": 50}"), // 无生成时间 → 不可比
                        report(2L, daysAround(1), "{not-json"), // 脏 JSON
                        report(3L, daysAround(1), "{\"别的维度\": 40}"))); // 无该维度

        assertTrue(service.effects(7L).isEmpty());
    }

    @Test
    void archivedOrUnconfirmedCraftsExcludedBySourceQuery() {
        // listByUser 里混入未确认套路（有练熟时间戳也不该出现：写路径保证不会，读路径再守一层）
        CraftNoteRecord analyzed = new CraftNoteRecord();
        analyzed.setId(8L);
        analyzed.setUserId(7L);
        analyzed.setTitle("排查异常套路");
        analyzed.setWhenToUse("异常时");
        analyzed.setContent("排查并恢复");
        analyzed.setTagsJson("[]");
        analyzed.setSource(CraftSource.AI_SUGGESTED);
        analyzed.setConfirmationStatus(CraftStatus.ANALYZED);
        analyzed.setPracticeCount(1);
        analyzed.setLastPracticedAt(PRACTICED_AT);
        when(craftMapper.listByUser(7L)).thenReturn(List.of(analyzed));
        when(reportMapper.listByUser(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(report(1L, daysAround(1), "{\"排障与异常恢复\": 70}")));

        // 候选池套路不配拥有证据卡：练熟回写只作用于 CONFIRMED（V50 SQL 已保证），
        // 但读路径按 confirmation_status 再挡一次，防止历史脏数据混入
        assertTrue(service.effects(7L).isEmpty());
    }

    @Test
    void secondaryReportsAndFailedReportsIgnored() {
        when(craftMapper.listByUser(7L)).thenReturn(List.of(
                mastered("并发异常恢复五步", "线上异常时", "排查根因，小步恢复，验证")));
        ReportCenterRow failed = report(9L, daysAround(-1), "{\"排障与异常恢复\": 10}");
        failed.setReportStatus("REPORT_FAILED");
        when(reportMapper.listByUser(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(
                        failed,
                        report(1L, daysAround(-1), "{\"排障与异常恢复\": 60}"),
                        report(2L, daysAround(1), "{\"排障与异常恢复\": 75}")));

        List<CraftEffectResponse> effects = service.effects(7L);

        assertEquals(1, effects.get(0).beforeCount()); // 失败报告不进前组
        assertEquals("IMPROVED", effects.get(0).status());
    }
}
