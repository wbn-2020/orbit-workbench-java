package com.orbitworkbench.aiconnection.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.ai.application.MemoryContext;
import com.orbitworkbench.aiconnection.domain.AiScenario;
import com.orbitworkbench.aiconnection.infrastructure.mapper.AiScenarioMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

/** V45 注入溯源：配置快照的 memory 键只在确实注入了记忆时出现，且只记结构不记正文。 */
class AiCallAuditRecorderMemoryTest {

    private final AiCallAuditRecorder recorder =
            new AiCallAuditRecorder(mock(AiScenarioMapper.class), new ObjectMapper());

    private static AiScenarioRouter.ResolvedRoute route() {
        AiConnectionService.AiConnectionRuntimeConfig primary =
                new AiConnectionService.AiConnectionRuntimeConfig(5L, 9L, "主用账户",
                        "https://gw.example/v1", "/chat/completions", "CHAT_COMPLETIONS",
                        "model-x", "sk-test", 60000);
        return new AiScenarioRouter.ResolvedRoute(primary, null, false,
                AiScenarioRouter.SOURCE_DEFAULT);
    }

    @Test
    void memoryAbsentFromSnapshotWhenNotInjected() {
        String json = recorder.snapshotJson(AiScenario.KNOWLEDGE_ANSWER, route(), 800, false, null,
                MemoryContext.NONE);

        assertFalse(json.contains("\"memory\""), "NONE 不应写 memory 键：" + json);
    }

    @Test
    void memoryNullYieldsNoMemoryKey() {
        String json = recorder.snapshotJson(AiScenario.KNOWLEDGE_ANSWER, route(), 800, false, null, null);

        assertFalse(json.contains("\"memory\""), "null 不应写 memory 键：" + json);
    }

    @Test
    void digestMemoryRecordsStructureNotText() {
        String secret = "## 画像\nJava 后端工程师，正在转型 AI。";
        MemoryContext memory = new MemoryContext("候选人画像快照：\n" + secret,
                "DIGEST", List.of(5L, 6L, 7L), 0);

        String json = recorder.snapshotJson(AiScenario.INTERVIEW_QUESTION, route(), 900, true, null, memory);

        assertTrue(json.contains("\"mode\":\"DIGEST\""), json);
        assertTrue(json.contains("\"factCount\":3"), json);
        assertTrue(json.contains("[5,6,7]"), json);
        assertTrue(json.contains("\"staleCount\":0"), json);
        // 只记结构：快照正文不得进快照
        assertFalse(json.contains("Java 后端工程师"), "记忆正文不得落审计快照：" + json);
    }

    @Test
    void factMemoryRecordsStaleCount() {
        MemoryContext memory = new MemoryContext("候选人已确认的画像事实：\n- [GOAL] 转型",
                "FACTS", List.of(9L), 1);

        String json = recorder.snapshotJson(AiScenario.KNOWLEDGE_ANSWER, route(), 800, false, null, memory);

        assertTrue(json.contains("\"mode\":\"FACTS\""), json);
        assertTrue(json.contains("\"staleCount\":1"), json);
        assertTrue(json.contains("\"factCount\":1"), json);
    }
}
