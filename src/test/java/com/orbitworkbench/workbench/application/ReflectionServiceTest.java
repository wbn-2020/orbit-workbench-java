package com.orbitworkbench.workbench.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orbitworkbench.preference.application.PreferenceService;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.workbench.domain.FocusMetricRow;
import com.orbitworkbench.workbench.domain.ReportMetricRow;
import com.orbitworkbench.workbench.domain.WorkMetricRow;
import com.orbitworkbench.workbench.infrastructure.mapper.ReflectionMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReflectionServiceTest {

    @Mock
    private ReflectionMapper mapper;
    @Mock
    private PreferenceService preferenceService;

    private ReflectionService serviceAt(String isoNow) {
        return new ReflectionService(mapper, preferenceService,
                Clock.fixed(Instant.parse(isoNow), ZoneId.of("UTC")));
    }

    @Test
    void weekWindowFollowsUserTimezoneAndGroupsDailyBuckets() {
        // 2026-09-13 是周日；东京时区下周一是 09-07（UTC 09-06 15:00）
        when(preferenceService.timezone(1L)).thenReturn(ZoneId.of("Asia/Tokyo"));
        FocusMetricRow focus = new FocusMetricRow();
        focus.setCreatedAt(Instant.parse("2026-09-08T01:00:00Z")); // 东京 09-08 10:00
        focus.setDurationMinutes(25);
        focus.setMode("FOCUS");
        FocusMetricRow breakRow = new FocusMetricRow();
        breakRow.setCreatedAt(Instant.parse("2026-09-08T02:00:00Z"));
        breakRow.setDurationMinutes(5);
        breakRow.setMode("BREAK"); // 休息不计入专注分钟
        when(mapper.listFocus(any(), any(), any())).thenReturn(List.of(focus, breakRow));
        WorkMetricRow log = new WorkMetricRow();
        log.setCreatedAt(Instant.parse("2026-09-08T03:00:00Z"));
        log.setCategory("PROJECT");
        log.setDistilled(true);
        when(mapper.listWorkLogs(any(), any(), any())).thenReturn(List.of(log));

        var response = serviceAt("2026-09-13T12:00:00Z").reflect(1L, "week", 0);

        assertEquals(LocalDate.parse("2026-09-07"), response.rangeStart());
        assertEquals(LocalDate.parse("2026-09-13"), response.rangeEnd());
        assertEquals(7, response.daily().size());
        assertEquals(25, response.daily().get(1).focusMinutes());
        assertEquals(1, response.daily().get(1).workLogs());
        assertEquals(0, response.daily().get(1).knowledgeCards());
        assertEquals(25, response.daily().stream().mapToInt(p -> p.focusMinutes()).sum());
        assertEquals(1, response.work().total());
        assertEquals(1, response.work().distilled());
        assertEquals(0, response.work().pendingDistill());
        assertEquals(1, response.work().byCategory().get("PROJECT"));
        // 本窗口起点必须落在东京本地周一零点
        assertEquals(Instant.parse("2026-09-06T15:00:00Z"),
                capturesWindowStart());
    }

    private Instant capturesWindowStart() {
        ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
        verify(mapper, times(3)).listWorkLogs(eq(1L), from.capture(), any());
        return from.getAllValues().get(0);
    }

    @Test
    void interviewScoresOnlyAverageCurrentRuleVersion() {
        when(preferenceService.timezone(1L)).thenReturn(ZoneId.of("UTC"));
        ReportMetricRow current = report("REPORT_READY", 80, "PASS",
                com.orbitworkbench.interview.application.InterviewReportService.SCORING_RULE_VERSION);
        ReportMetricRow legacy = report("REPORT_READY", 40, "FAIL", "rule-000000000000");
        ReportMetricRow noReport = report(null, null, null, null);
        when(mapper.listReports(any(), any(), any())).thenReturn(List.of(current, legacy, noReport));

        var response = serviceAt("2026-09-13T12:00:00Z").reflect(1L, "week", 0);

        var interview = response.interview();
        assertEquals(3, interview.sessions());
        assertEquals(1, interview.scored());
        assertEquals(80, interview.avgScore());
        assertEquals(1, interview.crossVersionSamples());
        assertEquals(1, interview.byRecommendation().get("PASS"));
        assertNull(interview.byRecommendation().get("FAIL"));
    }

    @Test
    void monthWindowCoversCalendarMonthAndPreviousPeriodEndsYesterday() {
        when(preferenceService.timezone(1L)).thenReturn(ZoneId.of("UTC"));

        var response = serviceAt("2026-09-15T12:00:00Z").reflect(1L, "month", 0);

        assertEquals(LocalDate.parse("2026-09-01"), response.rangeStart());
        assertEquals(LocalDate.parse("2026-09-30"), response.rangeEnd());
        assertEquals("month", response.period());
        // previous 窗口 = 8 月整月：起点 8-01，终点 9-01（半开区间，即 8-31 之后）
        ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> to = ArgumentCaptor.forClass(Instant.class);
        verify(mapper, times(3)).listWorkLogs(eq(1L), from.capture(), to.capture());
        assertEquals(Instant.parse("2026-08-01T00:00:00Z"), from.getAllValues().get(2));
        assertEquals(Instant.parse("2026-09-01T00:00:00Z"), to.getAllValues().get(2));
    }

    @Test
    void positiveOffsetRejected() {
        var service = serviceAt("2026-09-13T12:00:00Z");
        assertThrows(ApiException.class, () -> service.reflect(1L, "week", 1));
    }

    @Test
    void emptyWindowReturnsZeroesNotFabricatedNumbers() {
        when(preferenceService.timezone(1L)).thenReturn(ZoneId.of("UTC"));

        var response = serviceAt("2026-09-13T12:00:00Z").reflect(1L, "week", 0);

        assertEquals(0, response.interview().scored());
        assertNull(response.interview().avgScore());
        assertEquals(0, response.previous().focusMinutes());
        assertEquals(7, response.daily().size());
    }

    private static ReportMetricRow report(String status, Integer score, String recommendation,
                                          String ruleVersion) {
        ReportMetricRow row = new ReportMetricRow();
        row.setId(1L);
        row.setCreatedAt(Instant.parse("2026-09-09T02:00:00Z"));
        row.setStatus(status);
        row.setTotalScore(score);
        row.setHiringRecommendation(recommendation);
        row.setScoringRuleVersion(ruleVersion);
        return row;
    }
}
