package com.orbitworkbench.knowledge.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.craft.domain.CraftNoteRecord;
import com.orbitworkbench.craft.domain.CraftSource;
import com.orbitworkbench.craft.domain.CraftStatus;
import com.orbitworkbench.craft.infrastructure.mapper.CraftNoteMapper;
import com.orbitworkbench.interview.domain.ReportCenterRow;
import com.orbitworkbench.interview.infrastructure.mapper.InterviewReportMapper;
import com.orbitworkbench.learning.domain.LearningGoalRow;
import com.orbitworkbench.learning.infrastructure.mapper.LearningGoalMapper;
import com.orbitworkbench.studyplan.domain.StudyTaskRecord;
import com.orbitworkbench.studyplan.domain.StudyTaskStatus;
import com.orbitworkbench.studyplan.infrastructure.mapper.StudyTaskMapper;
import com.orbitworkbench.userfact.domain.UserFactRecord;
import com.orbitworkbench.userfact.domain.UserFactStatus;
import com.orbitworkbench.userfact.infrastructure.mapper.UserFactMapper;
import com.orbitworkbench.worklog.domain.WorkLogRow;
import com.orbitworkbench.worklog.infrastructure.mapper.WorkLogMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** V48：聚合命中/未命中/来源分类/维度低分优先。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PersonalContextServiceTest {

    @Mock private InterviewReportMapper reportMapper;
    @Mock private UserFactMapper userFactMapper;
    @Mock private StudyTaskMapper studyTaskMapper;
    @Mock private CraftNoteMapper craftNoteMapper;
    @Mock private WorkLogMapper workLogMapper;
    @Mock private LearningGoalMapper learningGoalMapper;

    private PersonalContextService service;

    @BeforeEach
    void setUp() {
        service = new PersonalContextService(reportMapper, userFactMapper, studyTaskMapper,
                craftNoteMapper, workLogMapper, learningGoalMapper, new ObjectMapper());
        when(reportMapper.listByUser(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(userFactMapper.listConfirmed(any(), anyInt())).thenReturn(List.of());
        when(studyTaskMapper.listByUser(any(), any())).thenReturn(List.of());
        when(craftNoteMapper.listConfirmedForPrompt(any())).thenReturn(List.of());
        when(workLogMapper.listByUser(any(), anyInt(), anyInt())).thenReturn(List.of());
        when(learningGoalMapper.listByUser(any(), anyInt(), anyInt())).thenReturn(List.of());
    }

    private static ReportCenterRow report() {
        ReportCenterRow row = new ReportCenterRow();
        row.setSessionTitle("真实模型链路验收面试");
        row.setReportStatus("REPORT_READY");
        row.setTotalScore(74);
        row.setHiringRecommendation("HOLD");
        row.setDimensionScoresJson("{\"表达结构\":88,\"排障与异常恢复\":55,\"项目实践能力\":60}");
        row.setKnowledgeGapsJson("[\"事务隔离级别\"]");
        return row;
    }

    @Test
    void emptyUserYieldsEmptyContext() {
        PersonalContextService.PersonalContext ctx = service.build(1L, "我哪个维度最弱");

        assertTrue(ctx.isEmpty());
        assertEquals("", ctx.context());
    }

    @Test
    void weakestDimensionQuestionAggregatesReport() {
        when(reportMapper.listByUser(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(report()));

        PersonalContextService.PersonalContext ctx = service.build(1L, "我哪个维度最弱？");

        assertFalse(ctx.isEmpty());
        assertTrue(ctx.context().contains("面试报告"));
        assertTrue(ctx.context().contains("总分 74"));
        // 维度按低分优先：最弱的两项应排在最高分之前
        int weakest = ctx.context().indexOf("排障与异常恢复 55");
        int strongest = ctx.context().indexOf("表达结构 88");
        assertTrue(weakest > 0 && strongest > 0 && weakest < strongest,
                "低分维度应排在前面：" + ctx.context());
        assertEquals("面试报告", ctx.sources().get(0).category());
    }

    @Test
    void sourcesAreNumberedInAscendingOrder() {
        when(reportMapper.listByUser(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(report()));
        UserFactRecord fact = new UserFactRecord();
        fact.setFactType("GOAL");
        fact.setTitle("转型目标");
        fact.setContent("转向 AI 应用开发");
        fact.setConfirmationStatus(UserFactStatus.CONFIRMED);
        when(userFactMapper.listConfirmed(any(), anyInt())).thenReturn(List.of(fact));

        PersonalContextService.PersonalContext ctx = service.build(1L, "我的面试维度和转型目标");

        List<com.orbitworkbench.knowledge.api.KnowledgeDtos.SourceItem> sources = ctx.sources();
        assertEquals(2, sources.size());
        assertEquals(1, sources.get(0).chunkNo());
        assertEquals(2, sources.get(1).chunkNo());
        assertEquals("画像事实", sources.get(1).category());
        // 编号与正文序号一致
        assertTrue(ctx.context().contains("[1] 面试报告"));
        assertTrue(ctx.context().contains("[2] 画像事实"));
    }

    @Test
    void pendingTasksInjectedWhenAskedAboutTodo() {
        StudyTaskRecord task = new StudyTaskRecord();
        task.setTitle("复习缓存一致性");
        task.setStatus(StudyTaskStatus.PLANNED);
        when(studyTaskMapper.listByUser(any(), any())).thenReturn(List.of(task));

        PersonalContextService.PersonalContext ctx = service.build(1L, "我有哪些待办复习任务？");

        assertTrue(ctx.context().contains("复习任务"));
        assertTrue(ctx.context().contains("复习缓存一致性"));
    }

    @Test
    void unrelatedQuestionDoesNotForceStudyTasks() {
        StudyTaskRecord task = new StudyTaskRecord();
        task.setTitle("复习缓存一致性");
        task.setStatus(StudyTaskStatus.PLANNED);
        when(studyTaskMapper.listByUser(any(), any())).thenReturn(List.of(task));

        // 与任务无关的问题不该把任务硬塞进上下文
        PersonalContextService.PersonalContext ctx = service.build(1L, "Redis 持久化原理");

        assertTrue(ctx.isEmpty(), "无关问题不应注入复习任务：" + ctx.context());
    }

    @Test
    void craftInjectedOnlyWhenMatched() {
        CraftNoteRecord craft = new CraftNoteRecord();
        craft.setCategory("PLAYBOOK");
        craft.setTitle("并发问题五步排查法");
        craft.setWhenToUse("排查线上并发异常时");
        craft.setSource(CraftSource.USER_ENTERED);
        craft.setConfirmationStatus(CraftStatus.CONFIRMED);
        when(craftNoteMapper.listConfirmedForPrompt(any())).thenReturn(List.of(craft));

        PersonalContextService.PersonalContext hit = service.build(1L, "我的并发排查套路是什么");
        assertTrue(hit.context().contains("方法论"));

        PersonalContextService.PersonalContext miss = service.build(1L, "今天天气");
        assertTrue(miss.isEmpty(), "无关问题不应注入方法论");
    }

    @Test
    void goalsInjectedWhenMatched() {
        LearningGoalRow goal = new LearningGoalRow();
        goal.setTitle("掌握 RAG 检索");
        goal.setStatus(com.orbitworkbench.learning.domain.LearningGoalStatus.ACTIVE);
        when(learningGoalMapper.listByUser(any(), anyInt(), anyInt())).thenReturn(List.of(goal));

        PersonalContextService.PersonalContext ctx = service.build(1L, "我的学习目标有哪些");

        assertTrue(ctx.context().contains("学习目标"));
        assertTrue(ctx.context().contains("掌握 RAG 检索"));
    }

    @Test
    void workLogsInjectedWhenMatched() {
        WorkLogRow log = new WorkLogRow();
        log.setCategory(com.orbitworkbench.worklog.domain.WorkLogCategory.INCIDENT);
        log.setTitle("HashMap 并发死循环");
        log.setContent("扩容时链表成环");
        when(workLogMapper.listByUser(any(), anyInt(), anyInt())).thenReturn(List.of(log));

        PersonalContextService.PersonalContext ctx = service.build(1L, "HashMap 并发问题我怎么处理的");

        assertTrue(ctx.context().contains("工作记录"));
        assertTrue(ctx.context().contains("HashMap 并发死循环"));
    }

    @Test
    void dirtyReportJsonDoesNotThrow() {
        ReportCenterRow row = report();
        row.setDimensionScoresJson("not-json");
        row.setKnowledgeGapsJson("{broken");
        when(reportMapper.listByUser(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(row));

        PersonalContextService.PersonalContext ctx = service.build(1L, "我的面试总分多少");

        // 脏 JSON 只是少一段上下文，不影响整体装配
        assertTrue(ctx.context().contains("面试报告"));
    }
}
