package com.orbitworkbench.content.application;

import com.orbitworkbench.agent.api.AgentRunDtos.RunCreatedResponse;
import com.orbitworkbench.agent.application.AgentRunService;
import com.orbitworkbench.artifact.api.ArtifactDtos.ArtifactDetailResponse;
import com.orbitworkbench.artifact.api.ArtifactDtos.ArtifactVersionResponse;
import com.orbitworkbench.artifact.application.ArtifactService;
import com.orbitworkbench.aiconnection.application.AiConnectionService;
import com.orbitworkbench.content.api.ContentDtos.ContentMaterialRequest;
import com.orbitworkbench.content.api.ContentDtos.ContentMaterialResponse;
import com.orbitworkbench.content.api.ContentDtos.ContentOperationRequest;
import com.orbitworkbench.content.api.ContentDtos.ContentProjectRequest;
import com.orbitworkbench.content.api.ContentDtos.ContentProjectResponse;
import com.orbitworkbench.content.api.ContentDtos.ContentProjectSummaryResponse;
import com.orbitworkbench.content.api.ContentDtos.ContentVersionResponse;
import com.orbitworkbench.content.domain.ContentProjectMaterialRecord;
import com.orbitworkbench.content.domain.ContentProjectRecord;
import com.orbitworkbench.content.domain.ContentVersionRecord;
import com.orbitworkbench.content.infrastructure.mapper.ContentProjectMapper;
import com.orbitworkbench.content.infrastructure.mapper.ContentVersionMapper;
import com.orbitworkbench.document.api.DocumentDtos.DocumentDetailResponse;
import com.orbitworkbench.document.api.DocumentDtos.DocumentText;
import com.orbitworkbench.document.application.DocumentService;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.shared.api.PageResult;
import com.orbitworkbench.task.api.TaskDtos.CreateTaskRequest;
import com.orbitworkbench.task.api.TaskDtos.TaskResponse;
import com.orbitworkbench.task.application.TaskService;
import com.orbitworkbench.task.domain.TaskRecord;
import com.orbitworkbench.workspace.application.WorkspaceService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContentProjectService {

    private static final String MARKDOWN = "MARKDOWN";
    private static final String CONTENT_MODULE = "CONTENT_CREATION";
    private static final Set<String> OPERATIONS = Set.of(
            "OUTLINE", "DRAFT", "REWRITE", "EXPAND", "COMPRESS", "REVIEW");
    private static final Set<String> SOURCE_REQUIRED_OPERATIONS = Set.of(
            "REWRITE", "EXPAND", "COMPRESS", "REVIEW");
    private static final Set<String> MATERIAL_TYPES = Set.of("DOCUMENT", "ARTIFACT");
    private static final int MAX_TASK_DESCRIPTION_CHARS = 1900;

    private final ContentProjectMapper projectMapper;
    private final ContentVersionMapper versionMapper;
    private final WorkspaceService workspaceService;
    private final DocumentService documentService;
    private final ArtifactService artifactService;
    private final AiConnectionService connectionService;
    private final TaskService taskService;
    private final AgentRunService agentRunService;

    public ContentProjectService(ContentProjectMapper projectMapper,
                                 ContentVersionMapper versionMapper,
                                 WorkspaceService workspaceService,
                                 DocumentService documentService,
                                 ArtifactService artifactService,
                                 AiConnectionService connectionService,
                                 TaskService taskService,
                                 AgentRunService agentRunService) {
        this.projectMapper = projectMapper;
        this.versionMapper = versionMapper;
        this.workspaceService = workspaceService;
        this.documentService = documentService;
        this.artifactService = artifactService;
        this.connectionService = connectionService;
        this.taskService = taskService;
        this.agentRunService = agentRunService;
    }

    @Transactional(readOnly = true)
    public PageResult<ContentProjectSummaryResponse> list(Long workspaceId,
                                                          int page,
                                                          int size) {
        if (workspaceId != null) {
            workspaceService.require(workspaceId);
        }
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        int offset = (normalizedPage - 1) * normalizedSize;
        List<ContentProjectSummaryResponse> items = projectMapper
                .findPage(workspaceId, offset, normalizedSize)
                .stream()
                .map(this::toSummary)
                .toList();
        return new PageResult<>(
                items,
                normalizedPage,
                normalizedSize,
                projectMapper.countPage(workspaceId));
    }

    @Transactional(readOnly = true)
    public ContentProjectResponse get(Long id) {
        ContentProjectRecord project = requireProject(id);
        return toResponse(project);
    }

    @Transactional
    public ContentProjectResponse create(ContentProjectRequest request) {
        workspaceService.require(request.workspaceId());
        requireUsableConnection(request.connectionId());
        validateOutputFormat(request.outputFormat());
        Instant now = Instant.now();
        ContentProjectRecord project = new ContentProjectRecord();
        project.setWorkspaceId(request.workspaceId());
        project.setConnectionId(request.connectionId());
        project.setTitle(requiredText(request.title(), "title", 120));
        project.setTopic(requiredText(request.topic(), "topic", 2000));
        project.setAudience(optionalText(request.audience(), 512));
        project.setStyle(optionalText(request.style(), 512));
        project.setOutputFormat(MARKDOWN);
        project.setStatus("DRAFT");
        project.setVersion(1L);
        project.setCreatedAt(now);
        project.setUpdatedAt(now);
        projectMapper.insert(project);
        return get(project.getId());
    }

    @Transactional
    public ContentProjectResponse update(Long id, ContentProjectRequest request) {
        ContentProjectRecord current = requireProjectForUpdate(id);
        if (!Objects.equals(current.getWorkspaceId(), request.workspaceId())) {
            throw invalid("内容项目不属于请求的工作空间");
        }
        requireUsableConnection(request.connectionId());
        validateOutputFormat(request.outputFormat());
        current.setConnectionId(request.connectionId());
        current.setTitle(requiredText(request.title(), "title", 120));
        current.setTopic(requiredText(request.topic(), "topic", 2000));
        current.setAudience(optionalText(request.audience(), 512));
        current.setStyle(optionalText(request.style(), 512));
        current.setOutputFormat(MARKDOWN);
        current.setUpdatedAt(Instant.now());
        long expectedVersion = expectedVersion(request.expectedVersion(), current.getVersion());
        if (projectMapper.update(current, expectedVersion) != 1) {
            throw conflict("内容项目已被其他请求修改，请刷新后重试");
        }
        return get(id);
    }

    @Transactional
    public ContentProjectResponse addMaterial(Long id, ContentMaterialRequest request) {
        ContentProjectRecord project = requireProjectForUpdate(id);
        String sourceType = normalizeMaterialType(request.sourceType());
        Long sourceId = positiveId(request.sourceId(), "sourceId");
        String sourceTitle = validateMaterial(project.getWorkspaceId(), sourceType, sourceId);
        ContentProjectMaterialRecord material = new ContentProjectMaterialRecord();
        material.setContentProjectId(project.getId());
        material.setSourceType(sourceType);
        material.setSourceId(sourceId);
        material.setRelationType(normalizeRelationType(request.relationType()));
        material.setSortOrder(projectMapper.findMaterials(id).size());
        material.setCreatedAt(Instant.now());
        material.setSourceTitle(sourceTitle);
        projectMapper.insertMaterial(material);
        touchProject(project);
        return get(id);
    }

    @Transactional
    public ContentProjectResponse removeMaterial(Long id, Long materialId) {
        ContentProjectRecord project = requireProjectForUpdate(id);
        if (projectMapper.deleteMaterial(id, positiveId(materialId, "materialId")) != 1) {
            throw new ApiException(HttpStatus.NOT_FOUND,
                    ErrorCode.RESOURCE_NOT_FOUND, "内容资料关联不存在");
        }
        touchProject(project);
        return get(id);
    }

    @Transactional
    public ContentVersionResponse generate(Long projectId,
                                           ContentOperationRequest request,
                                           String idempotencyKey) {
        return startOperation(projectId, request, idempotencyKey);
    }

    @Transactional
    public ContentVersionResponse review(Long projectId,
                                         ContentOperationRequest request,
                                         String idempotencyKey) {
        if (request == null || request.operation() == null
                || !"REVIEW".equalsIgnoreCase(request.operation())) {
            throw invalid("审阅接口只接受 REVIEW 操作");
        }
        return startOperation(projectId, request, idempotencyKey);
    }

    @Transactional(readOnly = true)
    public List<ContentVersionResponse> versions(Long projectId) {
        requireProject(projectId);
        return versionMapper.findByProjectId(projectId).stream()
                .map(version -> toVersionResponse(version, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public ContentVersionResponse version(Long projectId, Long versionId) {
        requireProject(projectId);
        ContentVersionRecord version = versionMapper.findById(versionId);
        if (version == null || !projectId.equals(version.getContentProjectId())) {
            throw new ApiException(HttpStatus.NOT_FOUND,
                    ErrorCode.RESOURCE_NOT_FOUND, "内容版本不存在");
        }
        return toVersionResponse(version, true);
    }

    private ContentVersionResponse startOperation(Long projectId,
                                                  ContentOperationRequest request,
                                                  String idempotencyKey) {
        ContentProjectRecord project = requireProjectForUpdate(projectId);
        if ("ARCHIVED".equals(project.getStatus())) {
            throw conflict("已归档内容项目不能创建新版本");
        }
        String operation = normalizeOperation(request == null ? null : request.operation());
        Long sourceVersionId = request == null ? null : request.sourceVersionId();
        ContentVersionRecord sourceVersion = sourceVersionId == null
                ? null : requireVersion(projectId, sourceVersionId);
        if (SOURCE_REQUIRED_OPERATIONS.contains(operation) && sourceVersion == null) {
            throw invalid(operation + " 操作必须指定已有内容版本");
        }
        String requestKey = normalizeRequestKey(idempotencyKey);
        if (requestKey != null) {
            ContentVersionRecord existing = versionMapper.findByRequestKey(projectId, requestKey);
            if (existing != null) {
                return toVersionResponse(existing, true);
            }
        }

        Instant now = Instant.now();
        ContentVersionRecord version = new ContentVersionRecord();
        version.setContentProjectId(projectId);
        version.setVersionNumber(versionMapper.findMaxVersionNumber(projectId) + 1);
        version.setOperation(operation);
        version.setStatus("QUEUED");
        version.setTitle(project.getTitle() + " - " + operation);
        version.setContentFormat(MARKDOWN);
        version.setRequestKey(requestKey);
        version.setCreatedAt(now);
        version.setUpdatedAt(now);
        versionMapper.insert(version);

        List<ContentProjectMaterialRecord> materials = projectMapper.findMaterials(projectId);
        List<Long> documentIds = materials.stream()
                .filter(material -> "DOCUMENT".equals(material.getSourceType()))
                .map(ContentProjectMaterialRecord::getSourceId)
                .toList();
        String taskDescription = buildTaskDescription(
                project, materials, sourceVersion, request == null ? null : request.instruction());
        String taskIdempotencyKey = requestKey == null
                ? null : "content-task-" + requestKey;
        TaskResponse task = taskService.create(
                new CreateTaskRequest(
                        project.getWorkspaceId(),
                        CONTENT_MODULE,
                        version.getTitle(),
                        taskDescription,
                        "REVIEW".equals(operation) ? "CONTENT_REVIEW" : "CONTENT_DRAFT",
                        "NORMAL",
                        documentIds,
                        project.getConnectionId()),
                taskIdempotencyKey,
                "CONTENT_PROJECT:" + projectId);
        RunCreatedResponse run = agentRunService.start(task.id(), null);
        if (versionMapper.attachTask(
                version.getId(), task.id(), run.runId(), "QUEUED", Instant.now()) != 1) {
            throw conflict("内容版本运行状态已变化，请重试");
        }
        return toVersionResponse(versionMapper.findById(version.getId()), false);
    }

    private String buildTaskDescription(ContentProjectRecord project,
                                        List<ContentProjectMaterialRecord> materials,
                                        ContentVersionRecord sourceVersion,
                                        String instruction) {
        StringBuilder description = new StringBuilder();
        appendLimited(description, "内容项目：", project.getTitle(), 120);
        appendLimited(description, "\n主题：", project.getTopic(), 500);
        appendLimited(description, "\n受众：", project.getAudience(), 180);
        appendLimited(description, "\n风格：", project.getStyle(), 180);
        appendLimited(description, "\n输出格式：", project.getOutputFormat(), 32);
        if (sourceVersion != null) {
            ArtifactVersionResponse sourceContent = readArtifactVersion(sourceVersion);
            if (sourceContent != null) {
                appendLimited(description, "\n已有版本内容：\n", sourceContent.content(), 700);
            }
        }
        appendLimited(description, "\n用户操作要求：\n", instruction, 500);
        if (!materials.isEmpty()) {
            description.append("\n参考资料：");
            for (ContentProjectMaterialRecord material : materials) {
                String content = readMaterialContent(material);
                appendLimited(
                        description,
                        "\n--- " + material.getSourceTitle() + " ---\n",
                        content,
                        320);
                if (description.length() >= MAX_TASK_DESCRIPTION_CHARS) {
                    break;
                }
            }
        }
        if (description.length() > MAX_TASK_DESCRIPTION_CHARS) {
            return description.substring(0, MAX_TASK_DESCRIPTION_CHARS);
        }
        return description.toString();
    }

    private String readMaterialContent(ContentProjectMaterialRecord material) {
        if ("DOCUMENT".equals(material.getSourceType())) {
            List<DocumentText> texts = documentService.readTextsByIds(
                    List.of(material.getSourceId()));
            return texts.isEmpty() ? "" : texts.get(0).content();
        }
        ArtifactDetailResponse artifact = artifactService.get(material.getSourceId());
        return artifact.content();
    }

    private String validateMaterial(Long workspaceId,
                                    String sourceType,
                                    Long sourceId) {
        if ("DOCUMENT".equals(sourceType)) {
            DocumentDetailResponse document = documentService.get(sourceId);
            if (!workspaceId.equals(document.workspaceId())) {
                throw invalid("资料不属于当前工作空间");
            }
            return document.originalName();
        }
        ArtifactDetailResponse artifact = artifactService.get(sourceId);
        TaskRecord task = taskService.requireTask(artifact.taskId());
        if (!workspaceId.equals(task.getWorkspaceId())) {
            throw invalid("成果不属于当前工作空间");
        }
        return artifact.title();
    }

    private ContentProjectRecord requireProject(Long id) {
        ContentProjectRecord project = projectMapper.findById(id);
        if (project == null) {
            throw new ApiException(HttpStatus.NOT_FOUND,
                    ErrorCode.RESOURCE_NOT_FOUND, "内容项目不存在");
        }
        return project;
    }

    private ContentProjectRecord requireProjectForUpdate(Long id) {
        ContentProjectRecord project = projectMapper.findByIdForUpdate(id);
        if (project == null) {
            throw new ApiException(HttpStatus.NOT_FOUND,
                    ErrorCode.RESOURCE_NOT_FOUND, "内容项目不存在");
        }
        return project;
    }

    private ContentVersionRecord requireVersion(Long projectId, Long versionId) {
        ContentVersionRecord version = versionMapper.findById(versionId);
        if (version == null || !projectId.equals(version.getContentProjectId())) {
            throw new ApiException(HttpStatus.NOT_FOUND,
                    ErrorCode.RESOURCE_NOT_FOUND, "内容版本不存在");
        }
        if (!"SUCCEEDED".equals(version.getStatus())) {
            throw conflict("只有已完成内容版本可以作为后续操作输入");
        }
        return version;
    }

    private ContentProjectResponse toResponse(ContentProjectRecord project) {
        List<ContentProjectMaterialResponse> materials = projectMapper
                .findMaterials(project.getId())
                .stream()
                .map(this::toMaterialResponse)
                .toList();
        List<ContentVersionResponse> versions = versionMapper
                .findByProjectId(project.getId())
                .stream()
                .map(version -> toVersionResponse(version, false))
                .toList();
        return new ContentProjectResponse(
                project.getId(),
                project.getWorkspaceId(),
                project.getConnectionId(),
                project.getTitle(),
                project.getTopic(),
                project.getAudience(),
                project.getStyle(),
                project.getOutputFormat(),
                project.getStatus(),
                project.getVersion(),
                materials,
                versions,
                project.getCreatedAt(),
                project.getUpdatedAt());
    }

    private ContentProjectSummaryResponse toSummary(ContentProjectRecord project) {
        ContentVersionRecord latest = versionMapper.findByProjectId(project.getId())
                .stream()
                .findFirst()
                .orElse(null);
        return new ContentProjectSummaryResponse(
                project.getId(),
                project.getWorkspaceId(),
                project.getConnectionId(),
                project.getTitle(),
                project.getTopic(),
                project.getOutputFormat(),
                project.getStatus(),
                project.getVersion(),
                latest == null ? null : latest.getVersionNumber(),
                latest == null ? null : latest.getStatus(),
                project.getCreatedAt(),
                project.getUpdatedAt());
    }

    private ContentMaterialResponse toMaterialResponse(
            ContentProjectMaterialRecord material) {
        return new ContentMaterialResponse(
                material.getId(),
                material.getContentProjectId(),
                material.getSourceType(),
                material.getSourceId(),
                material.getRelationType(),
                material.getSortOrder(),
                material.getSourceTitle(),
                material.getCreatedAt());
    }

    private ContentVersionResponse toVersionResponse(ContentVersionRecord version,
                                                     boolean includeContent) {
        if (version == null) {
            throw new ApiException(HttpStatus.NOT_FOUND,
                    ErrorCode.RESOURCE_NOT_FOUND, "内容版本不存在");
        }
        String content = null;
        if (includeContent && version.getArtifactId() != null
                && version.getArtifactVersionId() != null) {
            ArtifactVersionResponse artifactVersion = readArtifactVersion(version);
            content = artifactVersion == null ? null : artifactVersion.content();
        }
        return new ContentVersionResponse(
                version.getId(),
                version.getContentProjectId(),
                version.getVersionNumber(),
                version.getOperation(),
                version.getStatus(),
                version.getTitle(),
                version.getContentFormat(),
                content,
                version.getRequestKey(),
                version.getTaskId(),
                version.getSourceRunId(),
                version.getArtifactId(),
                version.getArtifactVersionId(),
                version.getErrorCode(),
                version.getErrorSummary(),
                version.getCreatedAt(),
                version.getUpdatedAt());
    }

    private ArtifactVersionResponse readArtifactVersion(ContentVersionRecord version) {
        if (version.getArtifactId() == null || version.getArtifactVersionId() == null) {
            return null;
        }
        return artifactService.version(version.getArtifactId(), version.getArtifactVersionId());
    }

    private void touchProject(ContentProjectRecord project) {
        project.setUpdatedAt(Instant.now());
        projectMapper.update(project, project.getVersion());
    }

    private void validateOutputFormat(String outputFormat) {
        if (!MARKDOWN.equalsIgnoreCase(outputFormat == null ? "" : outputFormat.trim())) {
            throw invalid("当前内容项目只支持 MARKDOWN 输出");
        }
    }

    private void requireUsableConnection(Long connectionId) {
        positiveId(connectionId, "connectionId");
        connectionService.getRuntimeConfig(connectionId);
    }

    private String normalizeOperation(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!OPERATIONS.contains(normalized)) {
            throw invalid("内容操作不支持");
        }
        return normalized;
    }

    private String normalizeMaterialType(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!MATERIAL_TYPES.contains(normalized)) {
            throw invalid("资料来源只支持 DOCUMENT 或 ARTIFACT");
        }
        return normalized;
    }

    private String normalizeRelationType(String value) {
        String normalized = value == null || value.isBlank()
                ? "REFERENCE" : value.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > 32) {
            throw invalid("relationType 不合法");
        }
        return normalized;
    }

    private String normalizeRequestKey(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 128) {
            throw invalid("Idempotency-Key 过长");
        }
        return normalized;
    }

    private long expectedVersion(Long requested, Long current) {
        if (requested != null && !requested.equals(current)) {
            throw conflict("内容项目版本已变化，请刷新后重试");
        }
        return current;
    }

    private Long positiveId(Long value, String field) {
        if (value == null || value < 1) {
            throw invalid(field + " 不合法");
        }
        return value;
    }

    private String requiredText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw invalid(field + " 不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw invalid(field + " 超出长度限制");
        }
        return normalized;
    }

    private String optionalText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() > maxLength
                ? normalized.substring(0, maxLength) : normalized;
    }

    private void appendLimited(StringBuilder builder,
                               String label,
                               String value,
                               int limit) {
        if (value == null || value.isBlank() || builder.length() >= MAX_TASK_DESCRIPTION_CHARS) {
            return;
        }
        builder.append(label);
        int remaining = Math.min(limit, MAX_TASK_DESCRIPTION_CHARS - builder.length());
        if (remaining > 0) {
            builder.append(value, 0, Math.min(value.length(), remaining));
        }
    }

    private ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_FAILED, message);
    }

    private ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT,
                ErrorCode.STATE_CONFLICT, message);
    }
}
