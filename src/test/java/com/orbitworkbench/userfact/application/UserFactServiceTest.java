package com.orbitworkbench.userfact.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.aiconnection.application.AiScenarioExecutionService;
import com.orbitworkbench.interview.infrastructure.mapper.InterviewReportMapper;
import com.orbitworkbench.jobprofile.infrastructure.mapper.JobProfileMapper;
import com.orbitworkbench.learning.infrastructure.mapper.LearningGoalMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.userfact.api.UserFactDtos.ConfirmUserFactRequest;
import com.orbitworkbench.userfact.api.UserFactDtos.CreateUserFactRequest;
import com.orbitworkbench.userfact.domain.UserFactRecord;
import com.orbitworkbench.userfact.domain.UserFactSource;
import com.orbitworkbench.userfact.domain.UserFactStatus;
import com.orbitworkbench.userfact.infrastructure.mapper.UserFactMapper;
import com.orbitworkbench.worklog.infrastructure.mapper.KnowledgeCardMapper;
import com.orbitworkbench.worklog.infrastructure.mapper.WorkLogMapper;
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
class UserFactServiceTest {

    @Mock
    private UserFactMapper factMapper;
    @Mock
    private JobProfileMapper jobProfileMapper;
    @Mock
    private WorkLogMapper workLogMapper;
    @Mock
    private KnowledgeCardMapper knowledgeCardMapper;
    @Mock
    private LearningGoalMapper learningGoalMapper;
    @Mock
    private InterviewReportMapper reportMapper;
    @Mock
    private AiScenarioExecutionService aiScenarioExecution;

    private UserFactService service;

    @BeforeEach
    void setUp() {
        service = new UserFactService(factMapper, jobProfileMapper, workLogMapper,
                knowledgeCardMapper, learningGoalMapper, reportMapper, aiScenarioExecution,
                new ObjectMapper());
    }

    @Test
    void manualCreateIsConfirmedImmediately() {
        when(factMapper.findByIdAndUser(any(), eq(1L))).thenAnswer(invocation -> {
            UserFactRecord saved = new UserFactRecord();
            saved.setId(invocation.getArgument(0));
            saved.setUserId(1L);
            saved.setFactType("GOAL");
            saved.setTitle("转 AI 方向");
            saved.setContent("目标是从 Java 后端转向 AI 应用开发");
            saved.setSource(UserFactSource.USER_ENTERED);
            saved.setConfirmationStatus(UserFactStatus.CONFIRMED);
            saved.setCreatedAt(Instant.now());
            return saved;
        });

        var response = service.createManual(1L,
                new CreateUserFactRequest("goal", "转 AI 方向", "目标是从 Java 后端转向 AI 应用开发", null));

        assertEquals("CONFIRMED", response.status());
        assertEquals("GOAL", response.factType());
        var captor = org.mockito.ArgumentCaptor.forClass(UserFactRecord.class);
        verify(factMapper).insert(captor.capture());
        assertEquals(UserFactSource.USER_ENTERED, captor.getValue().getSource());
        assertEquals(Integer.valueOf(100), captor.getValue().getConfidence());
    }

    @Test
    void confirmRejectsNonAnalyzedAndArchivesSuperseded() {
        UserFactRecord analyzed = analyzedFact(10L);
        analyzed.setSourceHint("supersedes:7,8");
        when(factMapper.findByIdAndUser(10L, 1L)).thenReturn(analyzed);
        when(factMapper.confirm(eq(10L), eq(1L), any(), any(), any(), any(), any())).thenReturn(1);

        service.confirm(1L, 10L, new ConfirmUserFactRequest("PREFERENCE", "偏好小模型", "测试环境优先小模型"));

        verify(factMapper).archive(eq(7L), eq(1L), eq("SUPERSEDED"), any());
        verify(factMapper).archive(eq(8L), eq(1L), eq("SUPERSEDED"), any());
    }

    @Test
    void confirmOnlyAcceptsAnalyzedCandidates() {
        UserFactRecord confirmed = analyzedFact(11L);
        confirmed.setConfirmationStatus(UserFactStatus.CONFIRMED);
        when(factMapper.findByIdAndUser(11L, 1L)).thenReturn(confirmed);

        assertThrows(ApiException.class, () -> service.confirm(1L, 11L,
                new ConfirmUserFactRequest("GOAL", "t", "c")));
        verify(factMapper, never()).confirm(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void distillRefusesWhileCandidatesPending() {
        when(factMapper.countAnalyzed(1L)).thenReturn(3L);
        assertThrows(ApiException.class, () -> service.distill(1L, null));
        verify(aiScenarioExecution, never()).executeText(any(), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.anyInt(), any());
    }

    @Test
    void distillRejectsWhenNoPersonalMaterial() {
        when(factMapper.countAnalyzed(1L)).thenReturn(0L);
        when(jobProfileMapper.findByUserId(1L)).thenReturn(null);
        // limit/offset 是原始 int，matcher 必须用 anyInt（any() 返回 null 会在拆箱处 NPE）
        when(workLogMapper.listByUser(eq(1L), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of());
        when(knowledgeCardMapper.listByUser(eq(1L), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of());
        when(learningGoalMapper.listByUser(eq(1L), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of());
        when(reportMapper.listByUser(eq(1L), any(), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of());

        assertThrows(ApiException.class, () -> service.distill(1L, null));
    }

    @Test
    void confirmedContextRespectsEmptyAndFormatting() {
        when(factMapper.listConfirmed(1L, 10)).thenReturn(List.of());
        assertEquals("", service.confirmedContext(1L));

        UserFactRecord fact = analyzedFact(1L);
        fact.setFactType("GOAL");
        fact.setTitle("转 AI 方向");
        fact.setContent("目标是从 Java 后端转向 AI 应用开发");
        fact.setConfirmationStatus(UserFactStatus.CONFIRMED);
        when(factMapper.listConfirmed(1L, 10)).thenReturn(List.of(fact));
        String context = service.confirmedContext(1L);
        assertTrue(context.startsWith("候选人已确认的画像事实"));
        assertTrue(context.contains("[GOAL]"));
        assertTrue(context.contains("转 AI 方向"));
    }

    private static UserFactRecord analyzedFact(long id) {
        UserFactRecord record = new UserFactRecord();
        record.setId(id);
        record.setUserId(1L);
        record.setFactType("PREFERENCE");
        record.setTitle("偏好小模型");
        record.setContent("测试环境优先小模型");
        record.setSource(UserFactSource.AI_SUGGESTED);
        record.setConfirmationStatus(UserFactStatus.ANALYZED);
        record.setConfidence(80);
        record.setCreatedAt(Instant.now());
        record.setUpdatedAt(Instant.now());
        return record;
    }
}
