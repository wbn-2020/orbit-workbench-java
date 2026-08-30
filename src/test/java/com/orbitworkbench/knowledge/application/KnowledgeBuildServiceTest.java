package com.orbitworkbench.knowledge.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orbitworkbench.knowledge.api.KnowledgeDtos.BuildResultResponse;
import com.orbitworkbench.project.application.ProjectVersionImportedEvent;
import com.orbitworkbench.project.domain.ProjectRecord;
import com.orbitworkbench.project.domain.ProjectVersionRecord;
import com.orbitworkbench.project.infrastructure.mapper.ProjectMapper;
import com.orbitworkbench.shared.api.ApiException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class KnowledgeBuildServiceTest {

    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private KnowledgeService knowledgeService;

    private KnowledgeBuildService service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeBuildService(projectMapper, knowledgeService);
    }

    @Test
    void autoBuildRunsChunkBuildAndMarksReadyWhenClaimed() {
        ProjectVersionImportedEvent event = new ProjectVersionImportedEvent(7L, 41L, 52L);
        when(projectMapper.tryMarkKnowledgeBuilding(eq(52L), eq(List.of("PENDING")), any()))
                .thenReturn(1);
        when(knowledgeService.build(7L, 41L, 52L)).thenReturn(new BuildResultResponse(9, 3));

        service.buildAfterImport(event);

        ArgumentCaptor<Integer> chunkCount = ArgumentCaptor.forClass(Integer.class);
        verify(projectMapper).markKnowledgeReady(eq(52L), chunkCount.capture(), any(), any());
        assertEquals(9, chunkCount.getValue());
        verify(projectMapper, never()).markKnowledgeFailed(anyLong(), any(), any());
    }

    @Test
    void autoBuildSkipsSilentlyWhenAnotherBuildAlreadyClaimedTheVersion() {
        ProjectVersionImportedEvent event = new ProjectVersionImportedEvent(7L, 41L, 52L);
        when(projectMapper.tryMarkKnowledgeBuilding(eq(52L), eq(List.of("PENDING")), any()))
                .thenReturn(0);

        service.buildAfterImport(event);

        verify(knowledgeService, never()).build(anyLong(), anyLong(), anyLong());
        verify(projectMapper, never()).markKnowledgeReady(anyLong(), anyInt(), any(), any());
        verify(projectMapper, never()).markKnowledgeFailed(anyLong(), any(), any());
    }

    @Test
    void autoBuildMarksFailedWithSummarizedErrorInsteadOfPropagating() {
        ProjectVersionImportedEvent event = new ProjectVersionImportedEvent(7L, 41L, 52L);
        when(projectMapper.tryMarkKnowledgeBuilding(eq(52L), eq(List.of("PENDING")), any()))
                .thenReturn(1);
        when(knowledgeService.build(7L, 41L, 52L))
                .thenThrow(new IllegalStateException("存储读取失败: projects/x.md"));

        service.buildAfterImport(event);

        ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);
        verify(projectMapper).markKnowledgeFailed(eq(52L), error.capture(), any());
        assertTrue(error.getValue().startsWith("IllegalStateException: 存储读取失败"));
        verify(projectMapper, never()).markKnowledgeReady(anyLong(), anyInt(), any(), any());
    }

    @Test
    void rebuildClaimsFromFailedOrReadyAndReturnsBuildResult() {
        when(projectMapper.findProjectByIdAndUserId(41L, 7L)).thenReturn(project());
        when(projectMapper.findVersionById(52L)).thenReturn(version(41L));
        when(projectMapper.tryMarkKnowledgeBuilding(
                eq(52L), eq(List.of("PENDING", "READY", "FAILED")), any()))
                .thenReturn(1);
        when(knowledgeService.build(7L, 41L, 52L)).thenReturn(new BuildResultResponse(4, 2));

        BuildResultResponse result = service.rebuild(7L, 41L, 52L);

        assertEquals(4, result.chunkCount());
        verify(projectMapper).markKnowledgeReady(eq(52L), eq(4), any(), any());
    }

    @Test
    void rebuildReturnsConflictWhenBuildAlreadyInProgress() {
        when(projectMapper.findProjectByIdAndUserId(41L, 7L)).thenReturn(project());
        when(projectMapper.findVersionById(52L)).thenReturn(version(41L));
        when(projectMapper.tryMarkKnowledgeBuilding(
                eq(52L), eq(List.of("PENDING", "READY", "FAILED")), any()))
                .thenReturn(0);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.rebuild(7L, 41L, 52L));

        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        verify(knowledgeService, never()).build(anyLong(), anyLong(), anyLong());
    }

    @Test
    void rebuildMarksFailedAndPropagatesWhenBuildThrows() {
        when(projectMapper.findProjectByIdAndUserId(41L, 7L)).thenReturn(project());
        when(projectMapper.findVersionById(52L)).thenReturn(version(41L));
        when(projectMapper.tryMarkKnowledgeBuilding(
                eq(52L), eq(List.of("PENDING", "READY", "FAILED")), any()))
                .thenReturn(1);
        when(knowledgeService.build(7L, 41L, 52L))
                .thenThrow(new ApiException(HttpStatus.BAD_GATEWAY,
                        com.orbitworkbench.shared.api.ErrorCode.UPSTREAM_UNAVAILABLE, "读取失败"));

        assertThrows(ApiException.class, () -> service.rebuild(7L, 41L, 52L));

        ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);
        verify(projectMapper).markKnowledgeFailed(eq(52L), error.capture(), any());
        assertTrue(error.getValue().contains("读取失败"));
    }

    @Test
    void rebuildTruncatesLongErrorMessagesToColumnLimit() {
        when(projectMapper.findProjectByIdAndUserId(41L, 7L)).thenReturn(project());
        when(projectMapper.findVersionById(52L)).thenReturn(version(41L));
        when(projectMapper.tryMarkKnowledgeBuilding(
                eq(52L), eq(List.of("PENDING", "READY", "FAILED")), any()))
                .thenReturn(1);
        when(knowledgeService.build(7L, 41L, 52L))
                .thenThrow(new IllegalStateException("长".repeat(2000)));

        assertThrows(IllegalStateException.class, () -> service.rebuild(7L, 41L, 52L));

        ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);
        verify(projectMapper).markKnowledgeFailed(eq(52L), error.capture(), any());
        assertTrue(error.getValue().length() <= 512);
    }

    @Test
    void rebuildRejectsVersionBelongingToAnotherProject() {
        when(projectMapper.findProjectByIdAndUserId(41L, 7L)).thenReturn(project());
        when(projectMapper.findVersionById(52L)).thenReturn(version(999L));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.rebuild(7L, 41L, 52L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        verify(projectMapper, never()).tryMarkKnowledgeBuilding(anyLong(), anyList(), any());
    }

    private ProjectRecord project() {
        ProjectRecord project = new ProjectRecord();
        project.setId(41L);
        project.setUserId(7L);
        return project;
    }

    private ProjectVersionRecord version(Long projectId) {
        ProjectVersionRecord version = new ProjectVersionRecord();
        version.setId(52L);
        version.setProjectId(projectId);
        version.setKnowledgeBuildStatus("FAILED");
        return version;
    }
}
