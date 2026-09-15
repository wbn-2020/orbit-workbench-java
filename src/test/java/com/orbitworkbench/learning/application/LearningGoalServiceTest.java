package com.orbitworkbench.learning.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orbitworkbench.learning.api.LearningGoalDtos.CreateLearningGoalRequest;
import com.orbitworkbench.learning.api.LearningGoalDtos.UpdateLearningGoalRequest;
import com.orbitworkbench.learning.domain.LearningGoalRow;
import com.orbitworkbench.learning.domain.LearningGoalStatus;
import com.orbitworkbench.learning.infrastructure.mapper.LearningGoalMapper;
import com.orbitworkbench.shared.api.ApiException;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LearningGoalServiceTest {

    @Mock
    private LearningGoalMapper mapper;

    @Mock
    private com.orbitworkbench.userfact.infrastructure.mapper.UserFactMapper userFactMapper;

    @Mock
    private com.orbitworkbench.studyplan.application.StudyTaskService studyTaskService;

    private LearningGoalRow row(long id, String title, String reason, String linkedSkill) {
        LearningGoalRow row = new LearningGoalRow();
        row.setId(id);
        row.setTitle(title);
        row.setReason(reason);
        row.setStatus(LearningGoalStatus.ACTIVE);
        row.setProgress(0);
        row.setLinkedSkill(linkedSkill);
        return row;
    }

    @Test
    void createRejectsBlankTitle() {
        LearningGoalService service = new LearningGoalService(mapper, userFactMapper, studyTaskService);

        ApiException exception = assertThrows(ApiException.class, () -> service.create(1L,
                new CreateLearningGoalRequest("  ", null, null), null));

        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, exception.getStatus());
        verify(mapper, never()).insert(any());
    }

    @Test
    void createNormalizesTitleAndTrimsOptionals() {
        when(mapper.findOwned(eq(1L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(row(5L, "掌握 RAG", null, null));
        LearningGoalService service = new LearningGoalService(mapper, userFactMapper, studyTaskService);

        service.create(1L, new CreateLearningGoalRequest("  掌握 RAG  ", "  学了用于知识库  ", "  检索  "), null);

        ArgumentCaptor<com.orbitworkbench.learning.domain.LearningGoalRecord> captor =
                ArgumentCaptor.forClass(com.orbitworkbench.learning.domain.LearningGoalRecord.class);
        verify(mapper).insert(captor.capture());
        Assertions.assertEquals("掌握 RAG", captor.getValue().getTitle());
        Assertions.assertEquals("学了用于知识库", captor.getValue().getReason());
        Assertions.assertEquals("检索", captor.getValue().getLinkedSkill());
        Assertions.assertEquals(LearningGoalStatus.ACTIVE, captor.getValue().getStatus());
        Assertions.assertEquals(0, captor.getValue().getProgress());
    }

    @Test
    void createReplaysSameContentOnIdempotencyKeyHit() {
        LearningGoalRow existing = row(9L, "掌握 RAG", null, null);
        when(mapper.findByIdempotencyKey(1L, "key-1")).thenReturn(existing);
        LearningGoalService service = new LearningGoalService(mapper, userFactMapper, studyTaskService);

        var response = service.create(1L,
                new CreateLearningGoalRequest("掌握 RAG", null, null), "key-1");

        assertEquals("9", response.id());
        verify(mapper, never()).insert(any());
    }

    @Test
    void createConflictsWhenIdempotencyKeyReusedWithDifferentContent() {
        when(mapper.findByIdempotencyKey(1L, "key-1")).thenReturn(row(9L, "掌握 RAG", null, null));
        LearningGoalService service = new LearningGoalService(mapper, userFactMapper, studyTaskService);

        ApiException exception = assertThrows(ApiException.class, () -> service.create(1L,
                new CreateLearningGoalRequest("学习向量检索", null, null), "key-1"));

        assertEquals(org.springframework.http.HttpStatus.CONFLICT, exception.getStatus());
        verify(mapper, never()).insert(any());
    }

    @Test
    void updateRejectsProgressOutsideBounds() {
        when(mapper.findOwned(1L, 3L)).thenReturn(row(3L, "目标", null, null));
        LearningGoalService service = new LearningGoalService(mapper, userFactMapper, studyTaskService);

        assertThrows(ApiException.class, () -> service.update(1L, 3L,
                new UpdateLearningGoalRequest("ACTIVE", 101)));
        assertThrows(ApiException.class, () -> service.update(1L, 3L,
                new UpdateLearningGoalRequest("ACTIVE", -1)));
        verify(mapper, never()).updateStatusAndProgress(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(), any());
    }

    @Test
    void updateRejectsInvalidStatus() {
        when(mapper.findOwned(1L, 3L)).thenReturn(row(3L, "目标", null, null));
        LearningGoalService service = new LearningGoalService(mapper, userFactMapper, studyTaskService);

        assertThrows(ApiException.class, () -> service.update(1L, 3L,
                new UpdateLearningGoalRequest("FINISHED", 10)));
        verify(mapper, never()).updateStatusAndProgress(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(), any());
    }

    @Test
    void updateWritesStatusAndProgressForOwnedGoal() {
        LearningGoalRow before = row(3L, "目标", null, null);
        LearningGoalRow after = row(3L, "目标", null, null);
        after.setStatus(LearningGoalStatus.DONE);
        after.setProgress(100);
        when(mapper.findOwned(1L, 3L)).thenReturn(before, after);
        when(mapper.updateStatusAndProgress(eq(3L), eq(1L), eq("DONE"), eq(100), any()))
                .thenReturn(1);
        LearningGoalService service = new LearningGoalService(mapper, userFactMapper, studyTaskService);

        var response = service.update(1L, 3L, new UpdateLearningGoalRequest("done", 100));

        assertSame(LearningGoalStatus.DONE, response.status());
        assertEquals(100, response.progress());
        verify(mapper).updateStatusAndProgress(eq(3L), eq(1L), eq("DONE"), eq(100), any());
    }

    @Test
    void updateReturns404ForForeignGoal() {
        when(mapper.findOwned(1L, 3L)).thenReturn(null);
        LearningGoalService service = new LearningGoalService(mapper, userFactMapper, studyTaskService);

        ApiException exception = assertThrows(ApiException.class, () -> service.update(1L, 3L,
                new UpdateLearningGoalRequest("ACTIVE", 10)));

        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, exception.getStatus());
        verify(mapper, never()).updateStatusAndProgress(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(), any());
    }

    // ---- V52：画像事实 → 学习目标 ----

    private static com.orbitworkbench.userfact.domain.UserFactRecord confirmedFact(long id) {
        com.orbitworkbench.userfact.domain.UserFactRecord fact =
                new com.orbitworkbench.userfact.domain.UserFactRecord();
        fact.setId(id);
        fact.setUserId(1L);
        fact.setFactType("KNOWLEDGE");
        fact.setTitle("MySQL 调优经验");
        fact.setContent("有实战索引优化经验，想系统化补齐");
        fact.setSource(com.orbitworkbench.userfact.domain.UserFactSource.USER_ENTERED);
        fact.setConfirmationStatus(com.orbitworkbench.userfact.domain.UserFactStatus.CONFIRMED);
        return fact;
    }

    @Test
    void createFromFactCopiesTitleAndLinksSource() {
        when(userFactMapper.findByIdAndUser(7L, 1L)).thenReturn(confirmedFact(7L));
        when(mapper.countBySourceFact(1L, 7L)).thenReturn(0);
        when(mapper.findOwned(eq(1L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(row(11L, "MySQL 调优经验", "有实战索引优化经验，想系统化补齐", null));
        LearningGoalService service = new LearningGoalService(mapper, userFactMapper, studyTaskService);

        var response = service.createFromFact(1L, 7L);

        assertEquals("11", response.id());
        ArgumentCaptor<com.orbitworkbench.learning.domain.LearningGoalRecord> captor =
                ArgumentCaptor.forClass(com.orbitworkbench.learning.domain.LearningGoalRecord.class);
        verify(mapper).insert(captor.capture());
        assertEquals("MySQL 调优经验", captor.getValue().getTitle());
        assertEquals("有实战索引优化经验，想系统化补齐", captor.getValue().getReason());
        assertEquals(7L, captor.getValue().getSourceFactId());
        assertEquals(LearningGoalStatus.ACTIVE, captor.getValue().getStatus());
    }

    @Test
    void createFromFactIsIdempotentPerFact() {
        when(userFactMapper.findByIdAndUser(7L, 1L)).thenReturn(confirmedFact(7L));
        when(mapper.countBySourceFact(1L, 7L)).thenReturn(1);
        LearningGoalService service = new LearningGoalService(mapper, userFactMapper, studyTaskService);

        ApiException exception = assertThrows(ApiException.class, () -> service.createFromFact(1L, 7L));

        assertEquals(org.springframework.http.HttpStatus.CONFLICT, exception.getStatus());
        verify(mapper, never()).insert(any());
    }

    @Test
    void createFromFactRejectsUnconfirmedFact() {
        com.orbitworkbench.userfact.domain.UserFactRecord analyzed = confirmedFact(7L);
        analyzed.setConfirmationStatus(com.orbitworkbench.userfact.domain.UserFactStatus.ANALYZED);
        when(userFactMapper.findByIdAndUser(7L, 1L)).thenReturn(analyzed);
        LearningGoalService service = new LearningGoalService(mapper, userFactMapper, studyTaskService);

        assertThrows(ApiException.class, () -> service.createFromFact(1L, 7L));
        verify(mapper, never()).insert(any());
    }

    @Test
    void createFromFactMissingReturns404() {
        when(userFactMapper.findByIdAndUser(99L, 1L)).thenReturn(null);
        LearningGoalService service = new LearningGoalService(mapper, userFactMapper, studyTaskService);

        ApiException exception = assertThrows(ApiException.class, () -> service.createFromFact(1L, 99L));
        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, exception.getStatus());
    }

    // ---- V58：目标拆任务 + 进度派生 ----

    private static com.orbitworkbench.studyplan.domain.GoalTaskStats stats(long goalId, long total, long done) {
        com.orbitworkbench.studyplan.domain.GoalTaskStats s =
                new com.orbitworkbench.studyplan.domain.GoalTaskStats();
        s.setGoalId(goalId);
        s.setTotal(total);
        s.setCompleted(done);
        return s;
    }

    @Test
    void addTaskRejectsForeignGoal() {
        when(mapper.findOwned(1L, 3L)).thenReturn(null);
        LearningGoalService service = new LearningGoalService(mapper, userFactMapper, studyTaskService);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.addTask(1L, 3L, "读两篇源码文章"));

        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, exception.getStatus());
        verify(studyTaskService, never()).generateFromGoal(any(), any(), any());
    }

    @Test
    void addTaskDelegatesAfterOwnershipCheck() {
        when(mapper.findOwned(1L, 3L)).thenReturn(row(3L, "目标", null, null));
        when(studyTaskService.generateFromGoal(1L, 3L, "读两篇源码文章")).thenReturn(1);
        LearningGoalService service = new LearningGoalService(mapper, userFactMapper, studyTaskService);

        var result = service.addTask(1L, 3L, "读两篇源码文章");

        assertEquals(1, result.get("created"));
    }

    @Test
    void addTaskPassesThroughIdempotentZero() {
        when(mapper.findOwned(1L, 3L)).thenReturn(row(3L, "目标", null, null));
        when(studyTaskService.generateFromGoal(1L, 3L, "同样的步骤")).thenReturn(0);
        LearningGoalService service = new LearningGoalService(mapper, userFactMapper, studyTaskService);

        var result = service.addTask(1L, 3L, "同样的步骤");

        assertEquals(0, result.get("created")); // 幂等：同名步骤不堆任务，如实回 created=0
    }

    @Test
    void listDerivesProgressFromTaskStats() {
        LearningGoalRow withTasks = row(4L, "拆过的目标", null, null);
        withTasks.setProgress(30); // 手存的旧值——有任务时不采信
        LearningGoalRow plain = row(5L, "没拆过的目标", null, null);
        plain.setProgress(40);
        when(mapper.listByUser(eq(1L), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(withTasks, plain));
        when(studyTaskService.listGoalTaskStats(1L)).thenReturn(List.of(stats(4L, 3, 2)));
        LearningGoalService service = new LearningGoalService(mapper, userFactMapper, studyTaskService);

        var responses = service.list(1L);

        var derived = responses.get(0);
        assertEquals(3, derived.taskCount());
        assertEquals(2, derived.completedTaskCount());
        assertEquals(67, derived.progress()); // round(2/3*100)，手存 30 被派生值覆盖
        assertTrue(derived.progressDerived());
        var manual = responses.get(1);
        assertEquals(0, manual.taskCount());
        assertEquals(40, manual.progress()); // 没拆任务时保留手动 PUT 的存储值
        assertFalse(manual.progressDerived());
    }
}
