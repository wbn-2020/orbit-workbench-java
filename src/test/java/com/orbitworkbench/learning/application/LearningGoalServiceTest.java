package com.orbitworkbench.learning.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        LearningGoalService service = new LearningGoalService(mapper, userFactMapper);

        ApiException exception = assertThrows(ApiException.class, () -> service.create(1L,
                new CreateLearningGoalRequest("  ", null, null), null));

        assertEquals(org.springframework.http.HttpStatus.BAD_REQUEST, exception.getStatus());
        verify(mapper, never()).insert(any());
    }

    @Test
    void createNormalizesTitleAndTrimsOptionals() {
        when(mapper.findOwned(eq(1L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(row(5L, "掌握 RAG", null, null));
        LearningGoalService service = new LearningGoalService(mapper, userFactMapper);

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
        LearningGoalService service = new LearningGoalService(mapper, userFactMapper);

        var response = service.create(1L,
                new CreateLearningGoalRequest("掌握 RAG", null, null), "key-1");

        assertEquals("9", response.id());
        verify(mapper, never()).insert(any());
    }

    @Test
    void createConflictsWhenIdempotencyKeyReusedWithDifferentContent() {
        when(mapper.findByIdempotencyKey(1L, "key-1")).thenReturn(row(9L, "掌握 RAG", null, null));
        LearningGoalService service = new LearningGoalService(mapper, userFactMapper);

        ApiException exception = assertThrows(ApiException.class, () -> service.create(1L,
                new CreateLearningGoalRequest("学习向量检索", null, null), "key-1"));

        assertEquals(org.springframework.http.HttpStatus.CONFLICT, exception.getStatus());
        verify(mapper, never()).insert(any());
    }

    @Test
    void updateRejectsProgressOutsideBounds() {
        when(mapper.findOwned(1L, 3L)).thenReturn(row(3L, "目标", null, null));
        LearningGoalService service = new LearningGoalService(mapper, userFactMapper);

        assertThrows(ApiException.class, () -> service.update(1L, 3L,
                new UpdateLearningGoalRequest("ACTIVE", 101)));
        assertThrows(ApiException.class, () -> service.update(1L, 3L,
                new UpdateLearningGoalRequest("ACTIVE", -1)));
        verify(mapper, never()).updateStatusAndProgress(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(), any());
    }

    @Test
    void updateRejectsInvalidStatus() {
        when(mapper.findOwned(1L, 3L)).thenReturn(row(3L, "目标", null, null));
        LearningGoalService service = new LearningGoalService(mapper, userFactMapper);

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
        LearningGoalService service = new LearningGoalService(mapper, userFactMapper);

        var response = service.update(1L, 3L, new UpdateLearningGoalRequest("done", 100));

        assertSame(LearningGoalStatus.DONE, response.status());
        assertEquals(100, response.progress());
        verify(mapper).updateStatusAndProgress(eq(3L), eq(1L), eq("DONE"), eq(100), any());
    }

    @Test
    void updateReturns404ForForeignGoal() {
        when(mapper.findOwned(1L, 3L)).thenReturn(null);
        LearningGoalService service = new LearningGoalService(mapper, userFactMapper);

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
        LearningGoalService service = new LearningGoalService(mapper, userFactMapper);

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
        LearningGoalService service = new LearningGoalService(mapper, userFactMapper);

        ApiException exception = assertThrows(ApiException.class, () -> service.createFromFact(1L, 7L));

        assertEquals(org.springframework.http.HttpStatus.CONFLICT, exception.getStatus());
        verify(mapper, never()).insert(any());
    }

    @Test
    void createFromFactRejectsUnconfirmedFact() {
        com.orbitworkbench.userfact.domain.UserFactRecord analyzed = confirmedFact(7L);
        analyzed.setConfirmationStatus(com.orbitworkbench.userfact.domain.UserFactStatus.ANALYZED);
        when(userFactMapper.findByIdAndUser(7L, 1L)).thenReturn(analyzed);
        LearningGoalService service = new LearningGoalService(mapper, userFactMapper);

        assertThrows(ApiException.class, () -> service.createFromFact(1L, 7L));
        verify(mapper, never()).insert(any());
    }

    @Test
    void createFromFactMissingReturns404() {
        when(userFactMapper.findByIdAndUser(99L, 1L)).thenReturn(null);
        LearningGoalService service = new LearningGoalService(mapper, userFactMapper);

        ApiException exception = assertThrows(ApiException.class, () -> service.createFromFact(1L, 99L));
        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, exception.getStatus());
    }
}
