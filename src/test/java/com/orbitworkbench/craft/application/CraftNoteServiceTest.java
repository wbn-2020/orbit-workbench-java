package com.orbitworkbench.craft.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.aiconnection.application.AiScenarioExecutionService;
import com.orbitworkbench.craft.api.CraftDtos.CraftNoteResponse;
import com.orbitworkbench.craft.api.CraftDtos.SaveCraftRequest;
import com.orbitworkbench.craft.domain.CraftNoteRecord;
import com.orbitworkbench.craft.domain.CraftSource;
import com.orbitworkbench.craft.domain.CraftStatus;
import com.orbitworkbench.craft.infrastructure.mapper.CraftNoteMapper;
import com.orbitworkbench.interview.infrastructure.mapper.InterviewReportMapper;
import com.orbitworkbench.jobprofile.infrastructure.mapper.JobProfileMapper;
import com.orbitworkbench.learning.infrastructure.mapper.LearningGoalMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.userfact.infrastructure.mapper.UserFactMapper;
import com.orbitworkbench.worklog.infrastructure.mapper.KnowledgeCardMapper;
import com.orbitworkbench.worklog.infrastructure.mapper.WorkLogMapper;
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
class CraftNoteServiceTest {

    @Mock private CraftNoteMapper craftMapper;
    @Mock private com.orbitworkbench.studyplan.infrastructure.mapper.StudyTaskMapper studyTaskMapper;
    @Mock private JobProfileMapper jobProfileMapper;
    @Mock private WorkLogMapper workLogMapper;
    @Mock private KnowledgeCardMapper knowledgeCardMapper;
    @Mock private LearningGoalMapper learningGoalMapper;
    @Mock private InterviewReportMapper reportMapper;
    @Mock private UserFactMapper userFactMapper;
    @Mock private AiScenarioExecutionService aiScenarioExecution;

    private CraftNoteService service;

    @BeforeEach
    void setUp() {
        service = new CraftNoteService(craftMapper, studyTaskMapper, jobProfileMapper, workLogMapper,
                knowledgeCardMapper, learningGoalMapper, reportMapper, userFactMapper,
                aiScenarioExecution, new ObjectMapper());
    }

    private static CraftNoteRecord confirmed(long id) {
        CraftNoteRecord r = new CraftNoteRecord();
        r.setId(id);
        r.setUserId(1L);
        r.setCategory("STORY");
        r.setTitle("项目讲述五步结构");
        r.setWhenToUse("面试讲项目时");
        r.setContent("1. 背景\n2. 职责\n3. 难点\n4. 结果\n5. 反思");
        r.setTagsJson("[\"面试\"]");
        r.setSource(CraftSource.USER_ENTERED);
        r.setConfirmationStatus(CraftStatus.CONFIRMED);
        r.setVersion(1);
        return r;
    }

    @Test
    void manualCreateIsConfirmedImmediately() {
        // insert 是 void，mock 后 id 不会被回填（findByIdAndUser 收到 null）；any() 可匹配 null
        when(craftMapper.findByIdAndUser(any(), eq(1L))).thenReturn(confirmed(1L));

        CraftNoteResponse response = service.createManual(1L, new SaveCraftRequest(
                "STORY", "项目讲述五步结构", "面试讲项目时", "1. 背景\n2. 职责", List.of("面试")));

        assertEquals("CONFIRMED", response.status());
        assertEquals(List.of("面试"), response.tags());
        verify(craftMapper).insert(any());
    }

    @Test
    void distillRefusesWhenCandidatesPending() {
        when(craftMapper.countAnalyzed(1L)).thenReturn(1L);

        assertThrows(ApiException.class, () -> service.distill(1L, null));
        verify(aiScenarioExecution, never()).executeText(any(), anyLong(), any(), any(), any(),
                anyInt(), any(), any(), any());
    }

    @Test
    void distillRefusesWithoutMaterial() {
        when(craftMapper.countAnalyzed(1L)).thenReturn(0L);
        when(jobProfileMapper.findByUserId(1L)).thenReturn(null);
        when(workLogMapper.listByUser(1L, 15, 0)).thenReturn(List.of());
        when(knowledgeCardMapper.listByUser(1L, 15, 0)).thenReturn(List.of());
        when(learningGoalMapper.listByUser(1L, 10, 0)).thenReturn(List.of());
        when(reportMapper.listByUser(any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of());
        when(userFactMapper.listConfirmed(1L, 10)).thenReturn(List.of());
        when(craftMapper.listConfirmedForPrompt(1L)).thenReturn(List.of());

        assertThrows(ApiException.class, () -> service.distill(1L, null));
    }

    @Test
    void confirmOnlyAcceptsAnalyzedCandidate() {
        when(craftMapper.findByIdAndUser(9L, 1L)).thenReturn(confirmed(9L));

        assertThrows(ApiException.class, () -> service.confirm(1L, 9L, new SaveCraftRequest(
                "STORY", "t", "w", "c", List.of())));
    }

    @Test
    void pinOnlyAllowsConfirmed() {
        CraftNoteRecord analyzed = confirmed(5L);
        analyzed.setConfirmationStatus(CraftStatus.ANALYZED);
        when(craftMapper.findByIdAndUser(5L, 1L)).thenReturn(analyzed);

        assertThrows(ApiException.class, () -> service.setPinned(1L, 5L, true));
        verify(craftMapper, never()).setPinned(any(), any(), eq(true), any());
    }

    @Test
    void archiveIsIdempotentForAlreadyArchived() {
        CraftNoteRecord archived = confirmed(6L);
        archived.setConfirmationStatus(CraftStatus.ARCHIVED);
        when(craftMapper.findByIdAndUser(6L, 1L)).thenReturn(archived);

        CraftNoteResponse response = service.archive(1L, 6L);

        assertEquals("ARCHIVED", response.status());
        // 已归档不再重复打更新
        verify(craftMapper, never()).archive(any(), any(), any());
    }

    @Test
    void updateRejectsArchivedCraft() {
        CraftNoteRecord archived = confirmed(7L);
        archived.setConfirmationStatus(CraftStatus.ARCHIVED);
        when(craftMapper.findByIdAndUser(7L, 1L)).thenReturn(archived);

        assertThrows(ApiException.class, () -> service.update(1L, 7L, new SaveCraftRequest(
                "STORY", "t", "w", "c", List.of())));
    }

    @Test
    void unknownCraftIsNotFound() {
        when(craftMapper.findByIdAndUser(99L, 1L)).thenReturn(null);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.archive(1L, 99L));
        assertEquals("套路不存在", exception.getMessage());
    }

    @Test
    void listParsesTagsAndKeepsCategoryOrder() {
        when(craftMapper.listByUser(1L)).thenReturn(List.of(confirmed(1L)));

        List<CraftNoteResponse> items = service.list(1L);

        assertEquals(1, items.size());
        assertEquals("STORY", items.get(0).category());
        assertTrue(items.get(0).tags().contains("面试"));
    }
}
