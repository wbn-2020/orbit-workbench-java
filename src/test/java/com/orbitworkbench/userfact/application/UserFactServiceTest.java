package com.orbitworkbench.userfact.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    @Mock
    private ProfileDigestService profileDigestService;

    private UserFactService service;

    @BeforeEach
    void setUp() {
        service = new UserFactService(factMapper, profileDigestService, jobProfileMapper, workLogMapper,
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
        assertEquals(com.orbitworkbench.ai.application.MemoryContext.NONE,
                service.confirmedContext(1L));

        UserFactRecord fact = analyzedFact(1L);
        fact.setFactType("GOAL");
        fact.setTitle("转 AI 方向");
        fact.setContent("目标是从 Java 后端转向 AI 应用开发");
        fact.setConfirmationStatus(UserFactStatus.CONFIRMED);
        when(factMapper.listConfirmed(1L, 10)).thenReturn(List.of(fact));
        com.orbitworkbench.ai.application.MemoryContext memory = service.confirmedContext(1L);
        assertTrue(memory.text().startsWith("候选人已确认的画像事实"));
        assertTrue(memory.text().contains("[GOAL]"));
        assertTrue(memory.text().contains("转 AI 方向"));
        // V45 溯源：FACTS 模式记录实际注入的事实 id
        assertEquals("FACTS", memory.mode());
        assertEquals(List.of(1L), memory.factIds());
        assertEquals(0, memory.staleCount());
    }

    @Test
    void confirmedContextPrefersFreshDigestOverPerFactListing() {
        com.orbitworkbench.userfact.domain.UserProfileDigestRecord digest =
                new com.orbitworkbench.userfact.domain.UserProfileDigestRecord();
        digest.setDigest("## 画像\nJava 后端转 AI 应用开发。");
        digest.setSourceFactIds("[5,6]");
        when(profileDigestService.freshDigestForInjection(1L)).thenReturn(digest);

        com.orbitworkbench.ai.application.MemoryContext memory = service.confirmedContext(1L);

        assertTrue(memory.text().startsWith("候选人画像快照"));
        assertTrue(memory.text().contains("Java 后端转 AI 应用开发"));
        // V45 溯源：DIGEST 模式带快照来源 id 集，且不再读逐条事实
        assertEquals("DIGEST", memory.mode());
        assertEquals(List.of(5L, 6L), memory.factIds());
        verify(factMapper, never()).listConfirmed(anyLong(), anyInt());
    }

    @Test
    void confirmedContextFallsBackWhenDigestExpired() {
        // freshDigestForInjection 默认返回 null（快照缺失/过期），走逐条模式
        UserFactRecord fact = analyzedFact(2L);
        fact.setFactType("CONTEXT");
        fact.setTitle("在职");
        fact.setContent("当前在一家做 SaaS 的公司做后端");
        fact.setConfirmationStatus(UserFactStatus.CONFIRMED);
        when(factMapper.listConfirmed(1L, 10)).thenReturn(List.of(fact));

        com.orbitworkbench.ai.application.MemoryContext memory = service.confirmedContext(1L);

        assertTrue(memory.text().startsWith("候选人已确认的画像事实"));
        assertTrue(memory.text().contains("在职"));
    }

    @Test
    void reaffirmBumpsLastSeenForConfirmedFact() {
        UserFactRecord confirmed = analyzedFact(12L);
        confirmed.setConfirmationStatus(UserFactStatus.CONFIRMED);
        when(factMapper.findByIdAndUser(12L, 1L)).thenReturn(confirmed);
        when(factMapper.reaffirm(eq(12L), eq(1L), any(), any())).thenReturn(1);

        service.reaffirm(1L, 12L);

        verify(factMapper).reaffirm(eq(12L), eq(1L), any(), any());
    }

    @Test
    void reaffirmRejectsNonConfirmedFact() {
        UserFactRecord analyzed = analyzedFact(13L);
        when(factMapper.findByIdAndUser(13L, 1L)).thenReturn(analyzed);

        assertThrows(ApiException.class, () -> service.reaffirm(1L, 13L));
        verify(factMapper, never()).reaffirm(any(), any(), any(), any());
    }

    @Test
    void confirmedContextAnnotatesStaleFactsButNotFreshOnes() {
        UserFactRecord stale = analyzedFact(20L);
        stale.setConfirmationStatus(UserFactStatus.CONFIRMED);
        stale.setTitle("Java 水平");
        stale.setContent("仅具备工作能力");
        stale.setConfirmedAt(Instant.now().minus(200, java.time.temporal.ChronoUnit.DAYS));
        stale.setLastSeenAt(stale.getConfirmedAt());
        UserFactRecord fresh = analyzedFact(21L);
        fresh.setConfirmationStatus(UserFactStatus.CONFIRMED);
        fresh.setTitle("近期目标");
        fresh.setContent("准备跨端项目");
        fresh.setConfirmedAt(Instant.now().minus(3, java.time.temporal.ChronoUnit.DAYS));
        fresh.setLastSeenAt(fresh.getConfirmedAt());
        when(factMapper.listConfirmed(1L, 10)).thenReturn(List.of(stale, fresh));

        com.orbitworkbench.ai.application.MemoryContext memory = service.confirmedContext(1L);
        String context = memory.text();

        assertTrue(context.contains("可能已过时"), "陈旧事实应带时效标注：" + context);
        // 新鲜事实紧跟其后，不应被标注
        int freshIdx = context.indexOf("近期目标");
        String afterFresh = context.substring(freshIdx);
        assertFalse(afterFresh.contains("可能已过时"), "新鲜事实不该被标注：" + afterFresh);
        // V45 溯源：过时条数与注入 id 集如实记录
        assertEquals(1, memory.staleCount());
        assertEquals(List.of(20L, 21L), memory.factIds());
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
