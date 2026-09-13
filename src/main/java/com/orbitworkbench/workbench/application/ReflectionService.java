package com.orbitworkbench.workbench.application;

import com.orbitworkbench.interview.application.InterviewReportService;
import com.orbitworkbench.preference.application.PreferenceService;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.workbench.api.ReflectionDtos.DailyPoint;
import com.orbitworkbench.workbench.api.ReflectionDtos.InterviewStats;
import com.orbitworkbench.workbench.api.ReflectionDtos.LearningStats;
import com.orbitworkbench.workbench.api.ReflectionDtos.PeriodTotals;
import com.orbitworkbench.workbench.api.ReflectionDtos.ReflectionResponse;
import com.orbitworkbench.workbench.api.ReflectionDtos.StudyStats;
import com.orbitworkbench.workbench.api.ReflectionDtos.WorkStats;
import com.orbitworkbench.workbench.domain.FocusMetricRow;
import com.orbitworkbench.workbench.domain.KnowledgeMetricRow;
import com.orbitworkbench.workbench.domain.LearningMetricRow;
import com.orbitworkbench.workbench.domain.ReportMetricRow;
import com.orbitworkbench.workbench.domain.StudyMetricRow;
import com.orbitworkbench.workbench.domain.WindowRow;
import com.orbitworkbench.workbench.domain.WorkMetricRow;
import com.orbitworkbench.workbench.infrastructure.mapper.ReflectionMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 周期复盘（v2 三模式收敛点）。把市场感知 / 工作沉淀 / 学习更新三条线放到同一个
 * 时间窗口（自然周或自然月）里对齐，并与上一周期总量做环比。
 *
 * <p>只读聚合，不写任何表。诚实口径：
 * <ul>
 *   <li>面试分数平均**只统计当前评分规则版本的样本**，跨版本样本单列计数、绝不混入——
 *       与报告中心同一口径（scoring_rule_version 变了分数不可比）；</li>
 *   <li>已完成但没有报告的面试如实计入 sessions 且不计入 scored，不从总数里消失；</li>
 *   <li>环比的「上一周期」按同一周期长度回退一个 offset，边界同样按用户时区切。</li>
 * </ul>
 * 窗口边界以用户时区的日历周（周一至周日）/ 日历月计算，Instant 传给 SQL 做半开区间过滤。
 */
@Service
public class ReflectionService {

    private static final int MAX_OFFSET = 24;

    private final ReflectionMapper mapper;
    private final PreferenceService preferenceService;
    private final Clock clock;

    @Autowired
    public ReflectionService(ReflectionMapper mapper, PreferenceService preferenceService) {
        this(mapper, preferenceService, Clock.systemUTC());
    }

    ReflectionService(ReflectionMapper mapper, PreferenceService preferenceService, Clock clock) {
        this.mapper = mapper;
        this.preferenceService = preferenceService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ReflectionResponse reflect(Long userId, String period, int offset) {
        if (offset > 0 || offset < -MAX_OFFSET) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "offset 只接受 0 或负数（最多回溯 " + MAX_OFFSET + " 个周期）");
        }
        ZoneId zone = preferenceService.timezone(userId);
        LocalDate today = LocalDate.now(clock.withZone(zone));

        boolean monthly = "month".equalsIgnoreCase(period);
        LocalDate start = monthly
                ? YearMonth.from(today).atDay(1).plusMonths(offset)
                : today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).plusWeeks(offset);
        LocalDate end = monthly ? start.plusMonths(1).minusDays(1) : start.plusDays(6);
        LocalDate previousStart = monthly ? start.minusMonths(1) : start.minusWeeks(1);
        LocalDate previousEnd = start.minusDays(1);

        Instant from = start.atStartOfDay(zone).toInstant();
        Instant to = end.plusDays(1).atStartOfDay(zone).toInstant();
        Instant prevFrom = previousStart.atStartOfDay(zone).toInstant();
        Instant prevTo = previousEnd.plusDays(1).atStartOfDay(zone).toInstant();

        String label = (monthly
                ? "月度复盘 · " + YearMonth.from(start)
                : "周度复盘 · " + start.get(java.time.temporal.WeekFields.ISO.weekBasedYear())
                        + " 年第 " + start.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear()) + " 周")
                + "（" + start + " ~ " + end + "）";

        WorkStats work = collectWork(userId, from, to);
        LearningStats learning = collectLearning(userId, from, to);
        StudyStats study = collectStudy(userId, from, to);
        List<ReportMetricRow> reportRows = mapper.listReports(userId, from, to);
        InterviewStats interview = collectInterview(reportRows);
        List<DailyPoint> daily = dailyPoints(userId, from, to, start, end, zone);

        PeriodTotals previous = previousTotals(userId, prevFrom, prevTo);

        return new ReflectionResponse(
                monthly ? "month" : "week", offset, start, end, label,
                work, learning, study, interview,
                daily, previous, InterviewReportService.SCORING_RULE_VERSION);
    }

    private WorkStats collectWork(Long userId, Instant from, Instant to) {
        List<WorkMetricRow> rows = mapper.listWorkLogs(userId, from, to);
        Map<String, Integer> byCategory = new LinkedHashMap<>();
        int distilled = 0;
        for (WorkMetricRow row : rows) {
            byCategory.merge(row.getCategory() == null ? "OTHER" : row.getCategory(), 1, Integer::sum);
            if (row.isDistilled()) {
                distilled++;
            }
        }
        return new WorkStats(rows.size(), byCategory, distilled, rows.size() - distilled);
    }

    private LearningStats collectLearning(Long userId, Instant from, Instant to) {
        int created = 0;
        int done = 0;
        for (LearningMetricRow row : mapper.listLearningGoals(userId, from, to)) {
            if (!row.getCreatedAt().isBefore(from) && row.getCreatedAt().isBefore(to)) {
                created++;
            }
            if ("DONE".equals(row.getStatus()) && !row.getUpdatedAt().isBefore(from)
                    && row.getUpdatedAt().isBefore(to)) {
                done++;
            }
        }
        return new LearningStats(created, done);
    }

    private StudyStats collectStudy(Long userId, Instant from, Instant to) {
        int created = 0;
        int completed = 0;
        for (StudyMetricRow row : mapper.listStudyTasks(userId, from, to)) {
            if (!row.getCreatedAt().isBefore(from) && row.getCreatedAt().isBefore(to)) {
                created++;
            }
            if ("COMPLETED".equals(row.getStatus()) && row.getUpdatedAt() != null
                    && !row.getUpdatedAt().isBefore(from) && row.getUpdatedAt().isBefore(to)) {
                completed++;
            }
        }
        long outstanding = mapper.countActiveStudyTasks(userId);
        return new StudyStats(completed, created, (int) outstanding);
    }

    private InterviewStats collectInterview(List<ReportMetricRow> rows) {
        String currentVersion = InterviewReportService.SCORING_RULE_VERSION;
        Map<String, Integer> byRecommendation = new LinkedHashMap<>();
        int scored = 0;
        int crossVersion = 0;
        long scoreSum = 0;
        for (ReportMetricRow row : rows) {
            // 结论分布只统计当前规则版本，历史版本的建议不并入现代分布
            String recommendation = row.getHiringRecommendation();
            boolean ready = "REPORT_READY".equals(row.getStatus());
            if (ready && row.getTotalScore() != null && currentVersion.equals(row.getScoringRuleVersion())) {
                scored++;
                scoreSum += row.getTotalScore();
            }
            if (ready && row.getTotalScore() != null
                    && row.getScoringRuleVersion() != null
                    && !currentVersion.equals(row.getScoringRuleVersion())) {
                crossVersion++;
            }
            if (ready && recommendation != null && currentVersion.equals(row.getScoringRuleVersion())) {
                byRecommendation.merge(recommendation, 1, Integer::sum);
            }
        }
        Integer avg = scored == 0 ? null : (int) Math.round((double) scoreSum / scored);
        return new InterviewStats(rows.size(), scored, avg, crossVersion, byRecommendation);
    }

    private List<DailyPoint> dailyPoints(Long userId, Instant from, Instant to,
                                         LocalDate start, LocalDate end, ZoneId zone) {
        Map<LocalDate, int[]> buckets = new LinkedHashMap<>();
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            buckets.put(day, new int[3]);
        }
        for (FocusMetricRow row : mapper.listFocus(userId, from, to)) {
            if ("FOCUS".equals(row.getMode())) {
                addToDay(buckets, row, zone, 0, row.getDurationMinutes());
            }
        }
        for (WorkMetricRow row : mapper.listWorkLogs(userId, from, to)) {
            addToDay(buckets, row, zone, 1, 1);
        }
        for (KnowledgeMetricRow row : mapper.listKnowledgeCards(userId, from, to)) {
            addToDay(buckets, row, zone, 2, 1);
        }
        List<DailyPoint> points = new ArrayList<>();
        buckets.forEach((day, counts) ->
                points.add(new DailyPoint(day, counts[0], counts[1], counts[2])));
        return points;
    }

    private void addToDay(Map<LocalDate, int[]> buckets, WindowRow row, ZoneId zone, int slot, int amount) {
        LocalDate day = row.getCreatedAt().atZone(zone).toLocalDate();
        int[] counts = buckets.get(day);
        if (counts != null) {
            counts[slot] += amount;
        }
    }

    private PeriodTotals previousTotals(Long userId, Instant from, Instant to) {
        int focus = 0;
        for (FocusMetricRow row : mapper.listFocus(userId, from, to)) {
            if ("FOCUS".equals(row.getMode())) {
                focus += row.getDurationMinutes();
            }
        }
        int workLogs = mapper.listWorkLogs(userId, from, to).size();
        int knowledge = mapper.listKnowledgeCards(userId, from, to).size();
        int study = 0;
        for (StudyMetricRow row : mapper.listStudyTasks(userId, from, to)) {
            if ("COMPLETED".equals(row.getStatus()) && row.getUpdatedAt() != null
                    && !row.getUpdatedAt().isBefore(from) && row.getUpdatedAt().isBefore(to)) {
                study++;
            }
        }
        return new PeriodTotals(focus, workLogs, knowledge, study);
    }
}
