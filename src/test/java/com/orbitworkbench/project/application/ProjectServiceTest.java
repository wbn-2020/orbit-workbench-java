package com.orbitworkbench.project.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orbitworkbench.project.domain.ProjectFileRecord;
import com.orbitworkbench.project.domain.ProjectRecord;
import com.orbitworkbench.project.domain.ProjectVersionRecord;
import com.orbitworkbench.project.infrastructure.mapper.ProjectMapper;
import com.orbitworkbench.storage.application.LocalStorageService;
import com.orbitworkbench.storage.domain.StoredFile;
import com.orbitworkbench.workspace.application.WorkspaceService;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectMapper mapper;
    @Mock
    private WorkspaceService workspaceService;
    @Mock
    private LocalStorageService storageService;
    @Mock
    private GitHubRepositoryImporter gitHubRepositoryImporter;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ProjectService service;

    @BeforeEach
    void setUp() {
        service = new ProjectService(
                mapper, workspaceService, new ProjectImportScanner(new DocumentTextExtractor()), storageService,
                gitHubRepositoryImporter, eventPublisher);
    }

    @Test
    void createPersistsAnImmutableVersionWithParsedAndExcludedFileInventory() throws Exception {
        AtomicReference<ProjectRecord> projectReference = new AtomicReference<>();
        AtomicReference<ProjectVersionRecord> versionReference = new AtomicReference<>();
        AtomicReference<ProjectFileRecord> fileReference = new AtomicReference<>();

        doAnswer(invocation -> {
            ProjectRecord project = invocation.getArgument(0);
            project.setId(41L);
            projectReference.set(project);
            return null;
        }).when(mapper).insertProject(any(ProjectRecord.class));
        doAnswer(invocation -> {
            ProjectVersionRecord version = invocation.getArgument(0);
            version.setId(52L);
            versionReference.set(version);
            return null;
        }).when(mapper).insertVersion(any(ProjectVersionRecord.class));
        doAnswer(invocation -> {
            fileReference.set(invocation.getArgument(0));
            return null;
        }).when(mapper).insertFile(any(ProjectFileRecord.class));
        when(mapper.findProjectByIdAndUserId(41L, 7L)).thenAnswer(
                invocation -> projectReference.get());
        when(mapper.findVersions(41L)).thenAnswer(
                invocation -> List.of(versionReference.get()));
        when(mapper.findFiles(anyLong())).thenAnswer(
                invocation -> List.of(fileReference.get()));
        when(storageService.storeProjectSource(any(), anyLong())).thenReturn(
                new StoredFile("projects/source.zip", 100L, "a".repeat(64)),
                new StoredFile("projects/readme.md", 10L, "b".repeat(64)));

        var result = service.create(7L, 3L, " 渠道协同平台 ", zipUpload());

        ArgumentCaptor<ProjectRecord> projectCaptor = ArgumentCaptor.forClass(ProjectRecord.class);
        verify(mapper).insertProject(projectCaptor.capture());
        assertEquals("渠道协同平台", projectCaptor.getValue().getName());
        assertEquals(1, result.versions().size());
        assertEquals("REVIEW_REQUIRED", result.versions().getFirst().status());
        assertEquals("README.md", result.versions().getFirst().files().getFirst().relativePath());
        assertEquals(64, fileReference.get().getRelativePathHash().length());
        verify(workspaceService).require(3L);
        ArgumentCaptor<ProjectVersionImportedEvent> eventCaptor =
                ArgumentCaptor.forClass(ProjectVersionImportedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(7L, eventCaptor.getValue().userId());
        assertEquals(41L, eventCaptor.getValue().projectId());
        assertEquals(52L, eventCaptor.getValue().versionId());
    }

    private MockMultipartFile zipUpload() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("README.md"));
            zip.write("# project".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return new MockMultipartFile(
                "file", "project.zip", "application/zip", output.toByteArray());
    }
}
