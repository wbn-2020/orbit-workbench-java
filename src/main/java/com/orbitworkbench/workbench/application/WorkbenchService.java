package com.orbitworkbench.workbench.application;

import com.orbitworkbench.workbench.api.WorkbenchDtos.AgendaItem;
import com.orbitworkbench.workbench.api.WorkbenchDtos.AssetCount;
import com.orbitworkbench.workbench.api.WorkbenchDtos.ModeCard;
import com.orbitworkbench.workbench.api.WorkbenchDtos.PipelineCheck;
import com.orbitworkbench.workbench.api.WorkbenchDtos.WorkbenchSummaryResponse;
import com.orbitworkbench.workbench.domain.WorkbenchLastEvaluation;
import com.orbitworkbench.workbench.infrastructure.mapper.WorkbenchMapper;
import com.orbitworkbench.preference.application.PreferenceService;
import com.orbitworkbench.schedule.api.ScheduleDtos.AgendaItemResponse;
import com.orbitworkbench.schedule.application.ScheduleService;
import com.orbitworkbench.schedule.domain.ScheduleStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 工作台首页聚合（v2）。所有指标都来自我们自己的模块或面试报告只读统计，
 * 不写任何表；今日待办（agenda）复用 schedule 模块的只读 agenda（日程 / 面试 / 复习 / 投递），
 * 只做映射与截断，来源为空时如实返回空数组而非编造。
 */
@Service
public class WorkbenchService {

    /** 首页只展示最近若干条待办，避免长列表把首页压成一屏滚动。 */
    private static final int AGENDA_MAX_ITEMS = 8;
    /** 上次出分面试超过该天数即提示再做一次市场感知（v2 定位：周期性机制）。 */
    private static final int STALE_INTERVIEW_DAYS = 30;
    private static final DateTimeFormatter AGENDA_TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final WorkbenchMapper mapper;
    private final ScheduleService scheduleService;
    private final PreferenceService preferenceService;
    private final Clock clock;

    @Autowired
    public WorkbenchService(WorkbenchMapper mapper, ScheduleService scheduleService,
                            PreferenceService preferenceService) {
        this(mapper, scheduleService, preferenceService, Clock.systemUTC());
    }

    WorkbenchService(WorkbenchMapper mapper, ScheduleService scheduleService,
                     PreferenceService preferenceService, Clock clock) {
        this.mapper = mapper;
        this.scheduleService = scheduleService;
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

        return new WorkbenchSummaryResponse(greeting, modes, assets, agenda(userId, zone, start, end),
                focusToday, pipeline(userId, now, today, pendingDistill, activeGoals));
    }

    /**
     * 成长管线体检（借鉴 EvoFlow 运营洞察）：按主链顺序逐项检查
     * 「AI 账户 → 项目资料 → 面试校准 → 沉淀 → 复习 → 记忆确认」，
     * 每项只报可数的真实数字，全部通过时返回空列表，界面显示「管线健康」。
     */
    private List<PipelineCheck> pipeline(Long userId, Instant now, LocalDate today,
                                         long pendingDistill, long activeGoals) {
        List<PipelineCheck> checks = new ArrayList<>();

        if (mapper.countEnabledConnections() == 0) {
            checks.add(new PipelineCheck("connection", "BLOCK", "还没有可用的 AI 账户",
                    "面试出题、报告与蒸馏都需要至少一个已启用的连接。",
                    "/settings/ai-connections", "去配置"));
        }
        if (mapper.countProjects(userId) == 0) {
            checks.add(new PipelineCheck("project", "BLOCK", "还没有导入过项目资料",
                    "面试与知识库的出题依据来自你的项目版本，导入后 AI 才能建立可追溯的画像。",
                    "/projects", "去导入"));
        }

        long readyReports = mapper.countReadyReports(userId);
        if (readyReports == 0) {
            checks.add(new PipelineCheck("interview", "ACTION", "还没完成过出分的模拟面试",
                    "一场面试就能校准市场位置并生成 11 维报告与复习任务。",
                    "/interviews/new", "开始面试"));
        } else {
            Instant lastEnded = mapper.lastInterviewEndedAt(userId);
            if (lastEnded != null
                    && java.time.Duration.between(lastEnded, now).toDays() >= STALE_INTERVIEW_DAYS) {
                checks.add(new PipelineCheck("interview-cadence", "STALE",
                        "上一场面试已过 " + java.time.Duration.between(lastEnded, now).toDays() + " 天",
                        "市场在变，建议每月做一次周期性感知与能力校准。",
                        "/interviews/new", "再测一次"));
            }
        }

        long failedReports = mapper.countFailedReports(userId);
        if (failedReports > 0) {
            checks.add(new PipelineCheck("report-retry", "ACTION",
                    failedReports + " 份报告生成失败待重试",
                    "失败原因已记录在报告中心，可换账户或修正配置后重跑。",
                    "/reports", "去处理"));
        }
        if (pendingDistill > 0) {
            checks.add(new PipelineCheck("distill", "ACTION",
                    pendingDistill + " 条工作记录还没蒸馏成知识卡片",
                    "经验不蒸馏就会随项目流失，蒸馏只要一次点击。",
                    "/work-sedimentation", "去蒸馏"));
        }
        long dueCards = mapper.countDueCards(userId, today);
        if (dueCards > 0) {
            checks.add(new PipelineCheck("review", "ACTION", dueCards + " 张知识卡片今日到复习期",
                    "按间隔复习节奏到期，过一遍记得更牢。",
                    "/knowledge", "去复习"));
        }
        long pendingFacts = mapper.countPendingUserFacts(userId);
        if (pendingFacts > 0) {
            checks.add(new PipelineCheck("memory", "ACTION",
                    pendingFacts + " 条画像建议待确认",
                    "确认后才会注入面试与问答上下文，忽略会归档留痕。",
                    "/profile/job", "去确认"));
        }
        if (activeGoals == 0 && readyReports > 0) {
            checks.add(new PipelineCheck("goal", "ACTION", "报告指出了缺口，但还没有进行中的学习目标",
                    "把技能缺口转成目标与复习计划，形成「感知 → 学习」闭环。",
                    "/learning-update", "立目标"));
        }
        long unpracticedCrafts = mapper.countUnpracticedCrafts(userId);
        if (unpracticedCrafts > 0) {
            checks.add(new PipelineCheck("craft", "ACTION",
                    unpracticedCrafts + " 条已确认方法论还没安排练习",
                    "套路沉淀了就要练：转成练习任务后进入复习计划，形成「沉淀 → 练习」闭环。",
                    "/crafts", "去安排"));
        }
        return List.copyOf(checks);
    }

    /** 复用 schedule 模块的只读 agenda：日程 / 面试 / 复习任务 / 投递跟进，按开始时间排序。 */
    private List<AgendaItem> agenda(Long userId, ZoneId zone, Instant start, Instant end) {
        List<AgendaItemResponse> source = scheduleService.agenda(userId, start, end);
        List<AgendaItem> items = new ArrayList<>();
        for (AgendaItemResponse item : source) {
            if (items.size() >= AGENDA_MAX_ITEMS) {
                break;
            }
            items.add(new AgendaItem(
                    item.sourceType() + ":" + item.sourceId(),
                    item.allDay() ? "全天" : AGENDA_TIME.format(item.startAt().atZone(zone)),
                    item.title(),
                    ScheduleStatus.COMPLETED.name().equals(item.status())));
        }
        return items;
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
