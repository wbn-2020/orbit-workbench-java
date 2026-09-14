package com.orbitworkbench.workbench.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import com.orbitworkbench.craft.api.CraftDtos.CraftEffectResponse;
import com.orbitworkbench.craft.application.CraftEffectService;
import com.orbitworkbench.craft.domain.CraftNoteRecord;
import com.orbitworkbench.craft.domain.CraftSource;
import com.orbitworkbench.craft.domain.CraftStatus;
import com.orbitworkbench.craft.infrastructure.mapper.CraftNoteMapper;
import com.orbitworkbench.interview.domain.ReportCenterRow;
import com.orbitworkbench.interview.infrastructure.mapper.InterviewReportMapper;
import com.orbitworkbench.learning.domain.LearningGoalRow;
import com.orbitworkbench.learning.domain.LearningGoalStatus;
import com.orbitworkbench.learning.infrastructure.mapper.LearningGoalMapper;
import com.orbitworkbench.studyplan.domain.StudyTaskRecord;
import com.orbitworkbench.studyplan.domain.StudyTaskSource;
import com.orbitworkbench.studyplan.domain.StudyTaskStatus;
import com.orbitworkbench.studyplan.infrastructure.mapper.StudyTaskMapper;
import com.orbitworkbench.userfact.domain.UserFactRecord;
import com.orbitworkbench.userfact.domain.UserFactSource;
import com.orbitworkbench.userfact.domain.UserFactStatus;
import com.orbitworkbench.userfact.infrastructure.mapper.UserFactMapper;
import com.orbitworkbench.workbench.api.GrowthThreadDtos.GrowthThread;
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
class GrowthThreadServiceTest {

    @Mock private UserFactMapper userFactMapper;
    @Mock private LearningGoalMapper learningGoalMapper;
    @Mock private CraftNoteMapper craftMapper;
    @Mock private StudyTaskMapper studyTaskMapper;
    @Mock private InterviewReportMapper reportMapper;
    @Mock private CraftEffectService craftEffectService;

    private GrowthThreadService service;

    @BeforeEach
    void setUp() {
        service = new GrowthThreadService(userFactMapper, learningGoalMapper, craftMapper,
                studyTaskMapper, reportMapper, craftEffectService);
    }

    private static CraftNoteRecord craft(long id, String title, boolean pinned) {
        CraftNoteRecord r = new CraftNoteRecord();
        r.setId(id);
        r.setUserId(7L);
        r.setCategory("PLAYBOOK");
        r.setTitle(title);
        r.setWhenToUse("w");
        r.setContent("c");
        r.setTagsJson("[]");
        r.setSource(CraftSource.USER_ENTERED);
        r.setConfirmationStatus(CraftStatus.CONFIRMED);
        r.setPinned(pinned);
        return r;
    }

    private static StudyTaskRecord task(long id, StudyTaskSource source, Long sourceId,
                                        String title, StudyTaskStatus status) {
        StudyTaskRecord r = new StudyTaskRecord();
        r.setId(id);
        r.setUserId(7L);
        r.setSourceType(source);
        r.setSourceId(sourceId);
        r.setTitle(title);
        r.setStatus(status);
        return r;
    }

    @Test
    void craftThreadChainsTasksAndCarriesEffectStatus() {
        when(studyTaskMapper.listByUser(7L, null)).thenReturn(List.of(
                task(21L, StudyTaskSource.CRAFT, 5L, "练习：并发排查五步", StudyTaskStatus.COMPLETED)));
        when(craftMapper.listByUser(7L)).thenReturn(List.of(craft(5L, "并发问题五步排查法", true)));
        when(craftEffectService.effects(7L)).thenReturn(List.of(
                new CraftEffectResponse(5L, "排障与异常恢复", 2, 28, 1, 55, "IMPROVED")));

        List<GrowthThread> threads = service.threads(7L);

        assertEquals(1, threads.size());
        GrowthThread thread = threads.get(0);
        assertEquals("CRAFT", thread.type());
        assertEquals("已确认 · 置顶", thread.origin().get(0).status());
        assertEquals(1, thread.steps().size());
        assertEquals("已完成", thread.steps().get(0).status());
        assertEquals("IMPROVED", thread.effect());
    }

    @Test
    void craftWithoutTaskNotChained() {
        when(studyTaskMapper.listByUser(7L, null)).thenReturn(List.of());
        when(craftMapper.listByUser(7L)).thenReturn(List.of(craft(5L, "没人练的套路", false)));

        assertTrue(service.threads(7L).isEmpty()); // 宁缺毋滥：没下游不硬画
    }

    @Test
    void factGoalThreadUsesLinkAndGoalStatus() {
        UserFactRecord fact = new UserFactRecord();
        fact.setId(10L);
        fact.setUserId(7L);
        fact.setFactType("KNOWLEDGE");
        fact.setTitle("技能栈");
        fact.setContent("c");
        fact.setSource(UserFactSource.USER_ENTERED);
        fact.setConfirmationStatus(UserFactStatus.CONFIRMED);
        when(userFactMapper.listByUser(7L)).thenReturn(List.of(fact));
        LearningGoalRow goal = new LearningGoalRow();
        goal.setId(40L);
        goal.setTitle("技能栈");
        goal.setStatus(LearningGoalStatus.ACTIVE);
        goal.setProgress(30);
        goal.setSourceFactId(10L);
        when(learningGoalMapper.listByUser(7L, 200, 0)).thenReturn(List.of(goal));

        List<GrowthThread> threads = service.threads(7L);

        assertEquals(1, threads.size());
        assertEquals("FACT", threads.get(0).type());
        assertEquals("推进中 · 30%", threads.get(0).steps().get(0).status());
        assertEquals("/profile/job", threads.get(0).origin().get(0).to());
    }

    @Test
    void goalWithOrphanFactLinkNotDrawn() {
        when(userFactMapper.listByUser(7L)).thenReturn(List.of());
        LearningGoalRow goal = new LearningGoalRow();
        goal.setId(41L);
        goal.setTitle("孤儿目标");
        goal.setStatus(LearningGoalStatus.ACTIVE);
        goal.setSourceFactId(999L); // 指向不存在的事实
        when(learningGoalMapper.listByUser(7L, 200, 0)).thenReturn(List.of(goal));

        assertTrue(service.threads(7L).isEmpty());
    }

    @Test
    void reportThreadSkipsMissingReport() {
        when(studyTaskMapper.listByUser(7L, null)).thenReturn(List.of(
                task(22L, StudyTaskSource.REPORT, 33L, "补一次降级压测", StudyTaskStatus.PLANNED),
                task(23L, StudyTaskSource.REPORT, 34L, "整理笔记", StudyTaskStatus.PLANNED)));
        ReportCenterRow alive = new ReportCenterRow();
        alive.setReportId(33L);
        alive.setSessionTitle("Java 二面");
        when(reportMapper.findOwned(7L, 33L)).thenReturn(alive);
        when(reportMapper.findOwned(7L, 34L)).thenReturn(null); // 报告被删——链不画

        List<GrowthThread> threads = service.threads(7L);

        assertEquals(1, threads.size());
        assertEquals("REPORT", threads.get(0).type());
        assertEquals("已出分", threads.get(0).origin().get(0).status());
        assertEquals("计划中", threads.get(0).steps().get(0).status());
        assertNull(threads.get(0).effect());
    }

    @Test
    void threadsCapped() {
        UserFactRecord fact = new UserFactRecord();
        fact.setId(1L);
        fact.setUserId(7L);
        fact.setTitle("t");
        fact.setConfirmationStatus(UserFactStatus.CONFIRMED);
        fact.setSource(UserFactSource.USER_ENTERED);
        when(userFactMapper.listByUser(7L)).thenReturn(List.of(fact));
        List<LearningGoalRow> goals = new java.util.ArrayList<>();
        for (int i = 0; i < 35; i++) {
            LearningGoalRow goal = new LearningGoalRow();
            goal.setId((long) i);
            goal.setTitle("g" + i);
            goal.setStatus(LearningGoalStatus.ACTIVE);
            goal.setSourceFactId(1L);
            goals.add(goal);
        }
        when(learningGoalMapper.listByUser(7L, 200, 0)).thenReturn(goals);

        assertEquals(30, service.threads(7L).size());
    }
}
