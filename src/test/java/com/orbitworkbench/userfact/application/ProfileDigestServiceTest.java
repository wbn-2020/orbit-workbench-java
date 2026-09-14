package com.orbitworkbench.userfact.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.userfact.api.UserFactDtos.DigestResponse;
import com.orbitworkbench.userfact.domain.UserFactRecord;
import com.orbitworkbench.userfact.domain.UserFactStatus;
import com.orbitworkbench.userfact.domain.UserProfileDigestRecord;
import com.orbitworkbench.userfact.infrastructure.mapper.UserFactMapper;
import com.orbitworkbench.userfact.infrastructure.mapper.UserProfileDigestMapper;
import java.time.Instant;
import java.util.ArrayList;
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
class ProfileDigestServiceTest {

    @Mock
    private UserFactMapper factMapper;
    @Mock
    private UserProfileDigestMapper digestMapper;
    @Mock
    private AiScenarioExecutionService aiScenarioExecution;

    private ProfileDigestService service;

    @BeforeEach
    void setUp() {
        service = new ProfileDigestService(factMapper, digestMapper, aiScenarioExecution,
                new ObjectMapper());
    }

    private static List<UserFactRecord> facts(int count) {
        List<UserFactRecord> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            UserFactRecord fact = new UserFactRecord();
            fact.setId((long) i);
            fact.setUserId(1L);
            fact.setFactType("GOAL");
            fact.setTitle("事实" + i);
            fact.setContent("内容 " + i);
            fact.setConfirmationStatus(UserFactStatus.CONFIRMED);
            list.add(fact);
        }
        return list;
    }

    @Test
    void refusesDigestBelowThresholdWithoutCallingModel() {
        when(factMapper.listConfirmedForPrompt(1L)).thenReturn(facts(3));

        assertThrows(ApiException.class, () -> service.digest(1L, null));
        verify(aiScenarioExecution, never()).executeText(any(), anyLong(), any(), any(), any(),
                anyInt(), any());
    }

    @Test
    void digestPersistsSnapshotWithSourceIdSet() {
        when(factMapper.listConfirmedForPrompt(1L)).thenReturn(facts(4));
        when(aiScenarioExecution.executeText(any(), eq(1L), any(), any(), any(), anyInt(), any()))
                .thenReturn("## 画像\n四条已确认事实的编译产物，足够长度以通过最短校验。");

        DigestResponse response = service.digest(1L, null);

        assertEquals(4, response.sourceCount());
        assertTrue(response.digest().startsWith("## 画像"));
        verify(digestMapper).upsert(any());
    }

    @Test
    void stripsCodeFenceFromModelOutput() {
        when(factMapper.listConfirmedForPrompt(1L)).thenReturn(facts(4));
        when(aiScenarioExecution.executeText(any(), eq(1L), any(), any(), any(), anyInt(), any()))
                .thenReturn("```markdown\n## 画像\n围栏包裹的快照正文，长度足够通过最短校验，应当被完整剥掉。\n```");

        DigestResponse response = service.digest(1L, null);

        assertTrue(response.digest().startsWith("## 画像"));
        assertTrue(!response.digest().contains("```"));
    }

    @Test
    void freshDigestMatchesCurrentFactSet() {
        stubDigest(digestMapper, "[1,2,3,4]", 4);
        when(factMapper.listConfirmedForPrompt(1L)).thenReturn(facts(4));

        com.orbitworkbench.userfact.domain.UserProfileDigestRecord record =
                service.freshDigestForInjection(1L);
        assertEquals("快照正文", record.getDigest());
        assertEquals("[1,2,3,4]", record.getSourceFactIds());
    }

    @Test
    void expiredDigestReturnsNullWhenFactsAdded() {
        stubDigest(digestMapper, "[1,2,3]", 3);
        when(factMapper.listConfirmedForPrompt(1L)).thenReturn(facts(4));

        assertNull(service.freshDigestForInjection(1L));
    }

    @Test
    void expiredDigestReturnsNullWhenFactsArchived() {
        // 快照含 id=9（当前集合没有）：归档后必须回退逐条模式
        stubDigest(digestMapper, "[1,2,3,4,9]", 5);
        when(factMapper.listConfirmedForPrompt(1L)).thenReturn(facts(4));

        assertNull(service.freshDigestForInjection(1L));
    }

    @Test
    void currentReportsStalenessDelta() {
        stubDigest(digestMapper, "[1,2,9]", 3);
        // 当前集合 {1,2,3,4}：新增 2（3,4），消失 1（9）
        when(factMapper.listConfirmedForPrompt(1L)).thenReturn(facts(4));

        DigestResponse response = service.current(1L);

        assertTrue(response.stale());
        assertEquals(2, response.factsAdded());
        assertEquals(1, response.factsRemoved());
    }

    @Test
    void missingSnapshotYieldsNullCurrentAndNullInjection() {
        when(digestMapper.findByUser(1L)).thenReturn(null);

        assertNull(service.current(1L));
        assertNull(service.freshDigestForInjection(1L));
    }

    /** 统一构造带正文的快照记录，减少样板。 */
    private static void stubDigest(UserProfileDigestMapper mapper, String ids, int count) {
        UserProfileDigestRecord record = new UserProfileDigestRecord();
        record.setId(1L);
        record.setUserId(1L);
        record.setDigest("快照正文");
        record.setSourceFactIds(ids);
        record.setSourceCount(count);
        record.setCreatedAt(Instant.now());
        record.setUpdatedAt(Instant.now());
        when(mapper.findByUser(1L)).thenReturn(record);
    }
}
