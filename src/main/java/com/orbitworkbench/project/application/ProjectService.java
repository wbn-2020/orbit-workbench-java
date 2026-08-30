package com.orbitworkbench.project.application;

import com.orbitworkbench.project.api.ProjectDtos.ProjectDetailResponse;
import com.orbitworkbench.project.api.ProjectDtos.ProjectSummaryResponse;
import com.orbitworkbench.project.api.ProjectDtos.ProjectVersionResponse;
import com.orbitworkbench.project.application.ProjectImportScanner.ScanResult;
import com.orbitworkbench.project.application.ProjectImportScanner.ScannedFile;
import com.orbitworkbench.project.domain.ProjectFileRecord;
import com.orbitworkbench.project.domain.ProjectRecord;
import com.orbitworkbench.project.domain.ProjectVersionRecord;
import com.orbitworkbench.project.infrastructure.mapper.ProjectMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.storage.application.LocalStorageService;
import com.orbitworkbench.storage.domain.StoredFile;
import com.orbitworkbench.workspace.application.WorkspaceService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ProjectService {

    private static final long MAX_SOURCE_BYTES = 20L * 1024 * 1024;

    private final ProjectMapper mapper;
    private final WorkspaceService workspaceService;
    private final ProjectImportScanner scanner;
    private final LocalStorageService storageService;
    private final GitHubRepositoryImporter gitHubRepositoryImporter;
    private final ApplicationEventPublisher eventPublisher;

    public ProjectService(ProjectMapper mapper,
                          WorkspaceService workspaceService,
                          ProjectImportScanner scanner,
                          LocalStorageService storageService,
                          GitHubRepositoryImporter gitHubRepositoryImporter,
                          ApplicationEventPublisher eventPublisher) {
        this.mapper = mapper;
        this.workspaceService = workspaceService;
        this.scanner = scanner;
        this.storageService = storageService;
        this.gitHubRepositoryImporter = gitHubRepositoryImporter;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<ProjectSummaryResponse> list(Long userId) {
        return mapper.findProjectsByUserId(userId).stream()
                .map(project -> ProjectSummaryResponse.from(
                        project, mapper.findLatestVersion(project.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectDetailResponse get(Long userId, Long projectId) {
        ProjectRecord project = requireProject(userId, projectId, false);
        List<ProjectVersionResponse> versions = mapper.findVersions(projectId).stream()
                .map(version -> ProjectVersionResponse.from(
                        version, mapper.findFiles(version.getId())))
                .toList();
        return new ProjectDetailResponse(
                project.getId(), project.getWorkspaceId(), project.getName(), project.getStatus(),
                versions, project.getCreatedAt(), project.getUpdatedAt());
    }

    @Transactional
    public ProjectDetailResponse create(Long userId,
                                        Long workspaceId,
                                        String name,
                                        MultipartFile source) {
        workspaceService.require(workspaceId);
        String normalizedName = normalizeProjectName(name);
        ScanInput scanInput = scan(source);
        Instant now = Instant.now();

        ProjectRecord project = new ProjectRecord();
        project.setUserId(userId);
        project.setWorkspaceId(workspaceId);
        project.setName(normalizedName);
        project.setStatus("ACTIVE");
        project.setCreatedAt(now);
        project.setUpdatedAt(now);
        mapper.insertProject(project);
        persistVersion(project, 1, scanInput, now);
        return get(userId, project.getId());
    }

    @Transactional
    public ProjectDetailResponse createFromGitHub(Long userId,
                                                   Long workspaceId,
                                                   String name,
                                                   String repositoryUrl) {
        workspaceService.require(workspaceId);
        String normalizedName = normalizeProjectName(name);
        ScanInput scanInput = scanGitHub(repositoryUrl);
        Instant now = Instant.now();

        ProjectRecord project = new ProjectRecord();
        project.setUserId(userId);
        project.setWorkspaceId(workspaceId);
        project.setName(normalizedName);
        project.setStatus("ACTIVE");
        project.setCreatedAt(now);
        project.setUpdatedAt(now);
        mapper.insertProject(project);
        persistVersion(project, 1, scanInput, now);
        return get(userId, project.getId());
    }

    @Transactional
    public ProjectDetailResponse importVersion(Long userId,
                                               Long projectId,
                                               MultipartFile source) {
        ProjectRecord project = requireProject(userId, projectId, true);
        ScanInput scanInput = scan(source);
        Instant now = Instant.now();
        int versionNumber = mapper.nextVersionNumber(projectId);
        persistVersion(project, versionNumber, scanInput, now);
        if (mapper.touchProject(projectId, now) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "项目状态已变化，请刷新后重试");
        }
        return get(userId, projectId);
    }

    @Transactional
    public ProjectDetailResponse importGitHubVersion(Long userId,
                                                      Long projectId,
                                                      String repositoryUrl) {
        ProjectRecord project = requireProject(userId, projectId, true);
        ScanInput scanInput = scanGitHub(repositoryUrl);
        Instant now = Instant.now();
        int versionNumber = mapper.nextVersionNumber(projectId);
        persistVersion(project, versionNumber, scanInput, now);
        if (mapper.touchProject(projectId, now) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "项目状态已变化，请刷新后重试");
        }
        return get(userId, projectId);
    }

    private ScanInput scan(MultipartFile source) {
        if (source == null || source.isEmpty() || source.getOriginalFilename() == null) {
            throw validation("请选择项目压缩包或文本/代码文件");
        }
        if (source.getSize() > MAX_SOURCE_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, ErrorCode.FILE_TOO_LARGE,
                    "项目导入文件超过 20 MB 限制");
        }
        try (InputStream input = source.getInputStream()) {
            ScanResult result = scanner.scan(source.getOriginalFilename(), input);
            return new ScanInput(result.sourceType(), simpleFileName(source.getOriginalFilename()),
                    result, source::getInputStream);
        } catch (IOException exception) {
            throw validation("无法读取项目导入文件");
        }
    }

    private ScanInput scanGitHub(String repositoryUrl) {
        GitHubRepositoryImporter.GitHubArchive archive =
                gitHubRepositoryImporter.importArchive(repositoryUrl);
        try (InputStream input = new ByteArrayInputStream(archive.content())) {
            ScanResult result = scanner.scan("repository.zip", input);
            return new ScanInput("GITHUB", archive.sourceFileName(), result,
                    () -> new ByteArrayInputStream(archive.content()));
        } catch (IOException exception) {
            throw validation("无法读取 GitHub 仓库归档");
        }
    }

    private void persistVersion(ProjectRecord project,
                                int versionNumber,
                                ScanInput scanInput,
                                Instant now) {
        List<StoredFile> storedFiles = new ArrayList<>();
        registerRollbackCleanup(storedFiles);
        try {
            StoredFile sourceFile;
            try (InputStream input = scanInput.content().openStream()) {
                sourceFile = storageService.storeProjectSource(input, MAX_SOURCE_BYTES);
            }
            storedFiles.add(sourceFile);

            ScanResult scan = scanInput.scan();
            ProjectVersionRecord version = new ProjectVersionRecord();
            version.setProjectId(project.getId());
            version.setVersionNumber(versionNumber);
            version.setSourceType(scanInput.sourceType());
            version.setSourceFileName(scanInput.sourceFileName());
            version.setSourceStorageRef(sourceFile.storageRef());
            version.setSourceContentHash(sourceFile.contentHash());
            version.setStatus(scan.failedCount() > 0 ? "PARTIAL" : "REVIEW_REQUIRED");
            version.setTotalFileCount(scan.files().size());
            version.setParsedFileCount(scan.parsedCount());
            version.setExcludedFileCount(scan.excludedCount());
            version.setFailedFileCount(scan.failedCount());
            version.setTotalSizeBytes(scan.totalSizeBytes());
            version.setCreatedAt(now);
            version.setUpdatedAt(now);
            mapper.insertVersion(version);

            for (ScannedFile scanned : scan.files()) {
                ProjectFileRecord file = new ProjectFileRecord();
                file.setProjectVersionId(version.getId());
                file.setRelativePath(scanned.relativePath());
                file.setRelativePathHash(pathHash(scanned.relativePath()));
                file.setMediaType(scanned.mediaType());
                file.setSizeBytes(scanned.sizeBytes());
                file.setStatus(scanned.status());
                file.setStatusReason(scanned.statusReason());
                file.setCreatedAt(now);
                if ("PARSED".equals(scanned.status())) {
                    StoredFile stored = storageService.storeProjectSource(
                            new ByteArrayInputStream(scanned.content()),
                            ProjectImportScanner.MAX_ENTRY_BYTES);
                    storedFiles.add(stored);
                    file.setStorageRef(stored.storageRef());
                    file.setContentHash(stored.contentHash());
                }
                mapper.insertFile(file);
            }
            eventPublisher.publishEvent(new ProjectVersionImportedEvent(
                    project.getUserId(), project.getId(), version.getId(), scan.failedCount()));
        } catch (IOException exception) {
            throw validation("无法读取项目导入文件");
        } catch (RuntimeException exception) {
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                moveToTrash(storedFiles, exception);
            }
            throw exception;
        }
    }

    private ProjectRecord requireProject(Long userId, Long projectId, boolean forUpdate) {
        ProjectRecord project = forUpdate
                ? mapper.findProjectByIdAndUserIdForUpdate(projectId, userId)
                : mapper.findProjectByIdAndUserId(projectId, userId);
        if (project == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "项目不存在");
        }
        return project;
    }

    private void registerRollbackCleanup(List<StoredFile> storedFiles) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    moveToTrash(storedFiles, null);
                }
            }
        });
    }

    private void moveToTrash(List<StoredFile> storedFiles, RuntimeException cause) {
        for (StoredFile file : storedFiles) {
            try {
                storageService.moveToTrash(file.storageRef());
            } catch (RuntimeException cleanupFailure) {
                if (cause != null) {
                    cause.addSuppressed(cleanupFailure);
                }
            }
        }
    }

    private String normalizeProjectName(String name) {
        if (name == null || name.isBlank()) {
            throw validation("项目名称不能为空");
        }
        String normalized = name.trim();
        if (normalized.length() > 128) {
            throw validation("项目名称不能超过 128 个字符");
        }
        return normalized;
    }

    private String simpleFileName(String name) {
        String portable = name.replace('\\', '/');
        return portable.substring(portable.lastIndexOf('/') + 1).trim();
    }

    private String pathHash(String relativePath) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(relativePath.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JRE 不支持 SHA-256", exception);
        }
    }

    private ApiException validation(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, message);
    }

    private record ScanInput(
            String sourceType,
            String sourceFileName,
            ScanResult scan,
            SourceContent content
    ) {
    }

    @FunctionalInterface
    private interface SourceContent {
        InputStream openStream() throws IOException;
    }
}
