package com.orbitworkbench.knowledge.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.aiconnection.application.*;
import com.orbitworkbench.knowledge.application.*;
import com.orbitworkbench.identity.application.OrbitUserDetails;
import com.orbitworkbench.identity.domain.AppUserRecord;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import reactor.core.publisher.Flux;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KnowledgeStreamRegressionTest {
    @Test
    void doneContainsOriginalSourcesIncludingEscapedPaths() throws Exception {
        var knowledge = mock(KnowledgeService.class);
        var execution = mock(AiScenarioExecutionService.class);
        var stream = mock(ScenarioStreamSession.class);
        var json = new ObjectMapper();
        var source = new KnowledgeDtos.SourceItem("docs/\"design\".md", 2, "source snippet");
        when(knowledge.prepareAsk(7L, "question", null, null)).thenReturn(
                new KnowledgeService.AskStreamPreparation(List.of(source), "prompt", false,
                        com.orbitworkbench.ai.application.MemoryContext.NONE));
        when(execution.stream(any(), any(), any(), any(), any(), anyInt(), any(), any(), any()))
                .thenReturn(stream);
        when(stream.deltas()).thenReturn(Flux.just("answer [1]"));
        var controller = new KnowledgeController(knowledge, mock(KnowledgeBuildService.class),
                mock(ProjectFactService.class), execution, json);
        var user = new AppUserRecord();
        user.setId(7L);
        user.setUsername("test");
        var principal = new UsernamePasswordAuthenticationToken(new OrbitUserDetails(user), null, List.of());
        var events = controller.askStream(new KnowledgeDtos.AskRequest("question", null, null), principal)
                .collectList().block();
        var done = json.readTree(events.getLast().data());
        assertEquals("answer [1]", done.path("answer").asText());
        assertEquals(json.valueToTree(List.of(source)), done.path("sources"));
        verify(stream).begin();
        verify(stream).succeed(10);
    }
}
