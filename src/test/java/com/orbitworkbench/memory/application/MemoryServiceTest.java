package com.orbitworkbench.memory.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.agent.domain.AgentRunRecord;
import com.orbitworkbench.agent.infrastructure.mapper.AgentRunMapper;
import com.orbitworkbench.memory.api.MemoryDtos.RuntimeMemoryCandidateRequest;
import com.orbitworkbench.memory.api.MemoryDtos.MemoryCandidateRequest;
import com.orbitworkbench.memory.domain.MemoryCandidateRecord;
import com.orbitworkbench.memory.infrastructure.mapper.MemoryCandidateMapper;
import com.orbitworkbench.memory.infrastructure.mapper.MemoryMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.task.application.TaskService;
import com.orbitworkbench.task.domain.TaskRecord;
import com.orbitworkbench.workflow.infrastructure.mapper.WorkflowRunMapper;
import com.orbitworkbench.workspace.application.WorkspaceService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemoryServiceTest {

    @Mock
    private MemoryMapper memoryMapper;
    @Mock
    private MemoryCandidateMapper candidateMapper;
    @Mock
    private WorkspaceService workspaceService;
    @Mock
    private AgentRunMapper agentRunMapper;
    @Mock
    private TaskService taskService;
    @Mock
    private WorkflowRunMapper workflowRunMapper;

    private MemoryService service;

    @BeforeEach
    void setUp() {
        service = new MemoryService(
                memoryMapper,
                candidateMapper,
                workspaceService,
                new ObjectMapper(),
                agentRunMapper,
                taskService,
                workflowRunMapper);
    }

    @Test
    void agentRunProposalIsBoundToSucceededRunWorkspaceAndProposedStatus() {
        AgentRunRecord run = new AgentRunRecord();
        run.setId(7L);
        run.setTaskId(9L);
        run.setStatus("SUCCEEDED");
        when(agentRunMapper.findById(7L)).thenReturn(run);
        TaskRecord task = new TaskRecord();
        task.setWorkspaceId(11L);
        when(taskService.requireTask(9L)).thenReturn(task);
        doAnswer(invocation -> {
            MemoryCandidateRecord candidate = invocation.getArgument(0);
            candidate.setId(21L);
            return null;
        }).when(candidateMapper).insert(any(MemoryCandidateRecord.class));

        service.proposeFromAgentRun(
                7L,
                new RuntimeMemoryCandidateRequest(
                        "FACT",
                        Map.of("topic", "java"),
                        null,
                        null));

        ArgumentCaptor<MemoryCandidateRecord> captor =
                ArgumentCaptor.forClass(MemoryCandidateRecord.class);
        verify(candidateMapper).insert(captor.capture());
        MemoryCandidateRecord candidate = captor.getValue();
        assertEquals(11L, candidate.getWorkspaceId());
        assertEquals("AGENT_RUN", candidate.getSourceType());
        assertEquals(7L, candidate.getSourceId());
        assertEquals("PROPOSED", candidate.getStatus());
    }

    @Test
    void runningAgentCannotProposeCandidate() {
        AgentRunRecord run = new AgentRunRecord();
        run.setStatus("RUNNING");
        when(agentRunMapper.findById(7L)).thenReturn(run);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.proposeFromAgentRun(
                        7L,
                        new RuntimeMemoryCandidateRequest(
                                "FACT", Map.of("topic", "java"), null, null)));

        assertEquals("MEMORY_CONFLICT", exception.getErrorCode().name());
    }

    @Test
    void runtimeProposalStillRejectsSensitiveContent() {
        AgentRunRecord run = new AgentRunRecord();
        run.setTaskId(9L);
        run.setStatus("SUCCEEDED");
        when(agentRunMapper.findById(7L)).thenReturn(run);
        TaskRecord task = new TaskRecord();
        task.setWorkspaceId(11L);
        when(taskService.requireTask(9L)).thenReturn(task);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.proposeFromAgentRun(
                        7L,
                        new RuntimeMemoryCandidateRequest(
                                "FACT",
                                Map.of("apiKey", "not-a-real-secret"),
                                null,
                                null)));

        assertEquals("MEMORY_SENSITIVE_CONTENT", exception.getErrorCode().name());
    }

    @Test
    void explicitOutputBlockCreatesCandidatesAndIsRemovedFromArtifactText() {
        AgentRunRecord run = new AgentRunRecord();
        run.setTaskId(9L);
        run.setStatus("SUCCEEDED");
        when(agentRunMapper.findById(7L)).thenReturn(run);
        TaskRecord task = new TaskRecord();
        task.setWorkspaceId(11L);
        when(taskService.requireTask(9L)).thenReturn(task);

        MemoryProposalSummary summary = service.proposeFromAgentOutput(
                7L,
                """
                        正文内容
                        ```memory-candidates
                        [{"memoryType":"PREFERENCE","content":{"language":"Java"},
                          "confidence":0.9}]
                        ```
                        """);

        assertEquals(1, summary.proposedCount());
        assertEquals(0, summary.rejectedCount());
        assertTrue(summary.blockFound());
        assertEquals("正文内容", service.stripCandidateBlocks(
                "正文内容\n```memory-candidates\n[]\n```"));
        verify(candidateMapper).insert(any(MemoryCandidateRecord.class));
    }

    @Test
    void multipleCandidateBlocksAreParsedIndependentlyAndAllAreStripped() {
        AgentRunRecord run = new AgentRunRecord();
        run.setTaskId(9L);
        run.setStatus("SUCCEEDED");
        when(agentRunMapper.findById(7L)).thenReturn(run);
        TaskRecord task = new TaskRecord();
        task.setWorkspaceId(11L);
        when(taskService.requireTask(9L)).thenReturn(task);
        doAnswer(invocation -> {
            MemoryCandidateRecord candidate = invocation.getArgument(0);
            candidate.setId(21L);
            return null;
        }).when(candidateMapper).insert(any(MemoryCandidateRecord.class));

        MemoryProposalSummary summary = service.proposeFromAgentOutput(
                7L,
                """
                        前文 [保留]
                        ```memory-candidates
                        [{"memoryType":"FACT","content":{"topic":"java"}}]
                        ```
                        中间内容
                        ```memory-candidates
                        [{"memoryType":"CONSTRAINT","content":{"format":"markdown"}}]
                        ```
                        后文
                        """);

        assertEquals(2, summary.proposedCount());
        assertEquals(0, summary.rejectedCount());
        assertEquals("前文 [保留]\n中间内容\n后文", service.stripCandidateBlocks(
                """
                        前文 [保留]
                        ```memory-candidates
                        [{"memoryType":"FACT","content":{"topic":"java"}}]
                        ```
                        中间内容
                        ```memory-candidates
                        [{"memoryType":"CONSTRAINT","content":{"format":"markdown"}}]
                        ```
                        后文
                        """));
        verify(candidateMapper, org.mockito.Mockito.times(2))
                .insert(any(MemoryCandidateRecord.class));
    }

    @Test
    void manualRuntimeSourceCannotBeForged() {
        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.propose(new MemoryCandidateRequest(
                        11L,
                        "FACT",
                        Map.of("topic", "java"),
                        "AGENT_RUN",
                        7L,
                        null,
                        null)));

        assertEquals("MEMORY_INVALID", exception.getErrorCode().name());
    }
}
