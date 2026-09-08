package com.orbitworkbench.workbench.application;

import com.orbitworkbench.workbench.api.WorkbenchDtos.AgendaItem;
import com.orbitworkbench.workbench.api.WorkbenchDtos.AssetCount;
import com.orbitworkbench.workbench.api.WorkbenchDtos.ModeCard;
import com.orbitworkbench.workbench.api.WorkbenchDtos.WorkbenchSummaryResponse;
import com.orbitworkbench.workbench.domain.WorkbenchLastEvaluation;
import com.orbitworkbench.workbench.infrastructure.mapper.WorkbenchMapper;
import com.orbitworkbench.preference.application.PreferenceService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 工作台首页聚合（v2）。所有指标都来自我们自己的模块或面试报告只读统计，
 * 不写任何表；今日待办（agenda）当前后端没有独立来源，返回空数组而非编造。
 */
@Service
public class WorkbenchService {

    private final WorkbenchMapper mapper;
    private final PreferenceService preferenceService;
    private final Clock clock;

    @Autowired
    public WorkbenchService(WorkbenchMapper mapper, PreferenceService preferenceService) {
        this(mapper, preferenceService, Clock.systemUTC());
    }

    WorkbenchService(WorkbenchMapper mapper, PreferenceService preferenceService, Clock clock) {
        this.mapper = mapper;
        this.preferenceService = preferenceService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public WorkbenchSummaryResponse summary(Long userId) {
        ZoneId zone = preferenceService.timezone(userId);
        String greeting = greeting(zone);

        WorkbenchLastEvaluation evaluation = mapper.lastInterviewGrade(userId);
        String grade = mapGrade(evaluation);
        long pendingDistill = mapper.countPendingDistill(userId);
        long activeGoals = mapper.countActiveGoals(userId);

        List<ModeCard> modes = List.of(
                new ModeCard("market-sensing", "市场感知",
                        "用模拟面试校准你的市场位置与技能缺口，在职也应周期性使用。",
                        "/interviews", "最近一次评估", grade),
                new ModeCard("work-sedimentation", "工作沉淀",
                        "记录每日工作并蒸馏为可复用知识，避免经验随项目流失。",
                        "/work-sedimentation", "待蒸馏记录", String.valueOf(pendingDistill)),
                new ModeCard("learning-update", "学习更新",
                        "围绕识别出的技能缺口定向学习，配合专注计时持续进阶。",
                        "/learning-update", "进行中目标", String.valueOf(activeGoals)));

        long workLogs = mapper.countWorkLogs(userId);
        long knowledge = mapper.countKnowledgeCards(userId);
        long goals = mapper.countLearningGoals(userId);
        long focusMinutes = mapper.sumFocusMinutesAll(userId);

        List<AssetCount> assets = List.of(
                new AssetCount("worklog", "工作记录", "/work-sedimentation", workLogs),
                new AssetCount("knowledge", "知识卡片", "/knowledge/ask", knowledge),
                new AssetCount("goals", "学习目标", "/learning-update", goals),
                new AssetCount("focus", "专注分钟", "/learning-update", focusMinutes));

        Instant now = Instant.now(clock);
        LocalDate today = now.atZone(zone).toLocalDate();
        Instant start = today.atStartOfDay(zone).toInstant();
        Instant end = today.plusDays(1).atStartOfDay(zone).toInstant();
        int focusToday = (int) mapper.focusMinutesToday(userId, start, end);

        return new WorkbenchSummaryResponse(greeting, modes, assets, List.of(), focusToday);
    }

    private static String mapGrade(WorkbenchLastEvaluation evaluation) {
        if (evaluation == null || evaluation.getHiringRecommendation() == null) {
            return "—";
        }
        return switch (evaluation.getHiringRecommendation()) {
            case "STRONG_PASS" -> "A";
            case "PASS" -> "B";
            case "HOLD" -> "C";
            case "FAIL" -> "D";
            default -> "—";
        };
    }

    private String greeting(ZoneId zone) {
        int hour = LocalTime.now(clock.withZone(zone)).getHour();
        String part = switch (hour) {
            case 0, 1, 2, 3, 4, 5 -> "凌晨好";
            case 6, 7, 8, 9, 10 -> "早上好";
            case 11, 12 -> "中午好";
            case 13, 14, 15, 16, 17 -> "下午好";
            case 18, 19, 20, 21 -> "晚上好";
            default -> "夜深了";
        };
        return part + "，保持节奏，今天也稳步积累。";
    }
}
