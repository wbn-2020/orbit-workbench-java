package com.orbitworkbench.task.application;

import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.shared.api.PageResult;
import com.orbitworkbench.task.api.TaskDtos.CreateTaskRequest;
import com.orbitworkbench.task.api.TaskDtos.TaskResponse;
import com.orbitworkbench.task.api.TaskDtos.UpdateTaskRequest;
import com.orbitworkbench.task.domain.TaskCreateIdempotencyRecord;
import com.orbitworkbench.task.domain.TaskRecord;
import com.orbitworkbench.task.infrastructure.mapper.TaskMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {

    private static final Set<String> EDITABLE_STATUSES = Set.of("DRAFT", "READY", "PAUSED");
    private static final Set<String> MODULE_TYPES = Set.of("TECH_LEARNING", "DATA_ANALYSIS");
    private static final Set<String> PRIORITIES = Set.of("LOW", "NORMAL", "HIGH");
    private static final Set<String> TECH_LEARNING_ARTIFACT_TYPES =
            Set.of("LEARNING_NOTE", "QUIZ", "SUMMARY");
    private static final Set<String> DATA_ANALYSIS_ARTIFACT_TYPES =
            Set.of("ANALYSIS_REPORT", "CHART_SPEC", "DATA_EXPORT");

    private final TaskMapper taskMapper;

    public TaskService(TaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    @Transactional(readOnly = true)
    public PageResult<TaskResponse> findPage(Long workspaceId,
                                             String status,
                                             String moduleType,
                                             int page,
                                             int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        int offset = (normalizedPage - 1) * normalizedSize;
        List<TaskResponse> items = taskMapper
                .findPage(workspaceId, normalizeOptional(status), normalizeOptional(moduleType), offset, normalizedSize)
                .stream()
                .map(this::toResponse)
                .toList();
        long total = taskMapper.countPage(
                workspaceId, normalizeOptional(status), normalizeOptional(moduleType));
        return new PageResult<>(items, normalizedPage, normalizedSize, total);
    }

    @Transactional(readOnly = true)
    public TaskResponse get(Long id) {
        return toResponse(requireTask(id));
    }

    @Transactional
    public TaskResponse create(CreateTaskRequest request) {
        return create(request, null);
    }

    @Transactional
    public TaskResponse create(CreateTaskRequest request, String idempotencyKey) {
        return create(request, idempotencyKey, null);
    }

    @Transactional
    public TaskResponse create(CreateTaskRequest request,
                               String idempotencyKey,
                               String idempotencyScope) {
        String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        validateValues(
                request.moduleType(), request.priority(), request.expectedArtifactType());
        if ("DATA_ANALYSIS".equals(request.moduleType().trim().toUpperCase())
                && (idempotencyScope == null || idempotencyScope.isBlank())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "DATA_ANALYSIS 必须通过数据分析任务接口创建");
        }
        List<Long> documentIds = normalizedDocumentIds(request.documentIds());
        String moduleType = request.moduleType().trim().toUpperCase();
        String title = normalizeRequiredText(request.title(), "title", 120);
        String description = normalizeRequiredText(request.description(), "description", 2000);
        String expectedArtifactType = request.expectedArtifactType().trim().toUpperCase();
        String priority = request.priority().trim().toUpperCase();
        String fingerprint = normalizedIdempotencyKey == null
                ? null
                : requestFingerprint(
                        request.workspaceId(),
                        request.connectionId(),
                        moduleType,
                        title,
                        description,
                        expectedArtifactType,
                        priority,
                        documentIds,
                        idempotencyScope);

        boolean ownsReservation = normalizedIdempotencyKey == null
                || taskMapper.insertCreateReservation(
                normalizedIdempotencyKey, fingerprint, Instant.now()) == 1;
        if (!ownsReservation) {
            return returnReservedTask(normalizedIdempotencyKey, fingerprint);
        }

        validateReferences(request.workspaceId(), request.connectionId(), documentIds);

        Instant now = Instant.now();
        TaskRecord task = new TaskRecord();
        task.setWorkspaceId(request.workspaceId());
        task.setConnectionId(request.connectionId());
        task.setModuleType(moduleType);
        task.setTitle(title);
        task.setDescription(description);
        task.setExpectedArtifactType(expectedArtifactType);
        task.setPriority(priority);
        task.setStatus("READY");
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskMapper.insert(task);
        replaceDocuments(task.getId(), documentIds);

        if (normalizedIdempotencyKey != null
                && taskMapper.attachCreatedTask(
                normalizedIdempotencyKey, fingerprint, task.getId(), Instant.now()) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.DUPLICATE_REQUEST,
                    "任务幂等请求状态已变化，请重试");
        }
        return toResponse(taskMapper.findById(task.getId()));
    }

    private TaskResponse returnReservedTask(String idempotencyKey,
                                            String fingerprint) {
        TaskCreateIdempotencyRecord reservation =
                taskMapper.findCreateReservationForUpdate(idempotencyKey);
        if (reservation == null) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.DUPLICATE_REQUEST,
                    "任务幂等请求未能完成，请重试");
        }
        if (!fingerprint.equals(reservation.getRequestFingerprint())) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.DUPLICATE_REQUEST,
                    "Idempotency-Key 已用于其他任务请求");
        }
        if (reservation.getTaskId() == null) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.DUPLICATE_REQUEST,
                    "任务幂等请求仍在处理中，请重试");
        }
        TaskRecord existing = taskMapper.findById(reservation.getTaskId());
        if (existing == null) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.DUPLICATE_REQUEST,
                    "任务幂等结果不存在，请重试");
        }
        return toResponse(existing);
    }

    @Transactional
    public TaskResponse update(Long id, UpdateTaskRequest request) {
        return update(id, request, null);
    }

    @Transactional
    public TaskResponse update(Long id,
                               UpdateTaskRequest request,
                               String updateScope) {
        TaskRecord current = requireTaskForUpdate(id);
        if (!EDITABLE_STATUSES.contains(current.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "当前任务状态不允许修改核心输入");
        }
        validateValues(
                request.moduleType(), request.priority(), request.expectedArtifactType());
        String requestedModuleType = request.moduleType().trim().toUpperCase();
        if (!requestedModuleType.equals(current.getModuleType())) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "moduleType 创建后不可修改");
        }
        if ("DATA_ANALYSIS".equals(current.getModuleType())
                && (updateScope == null || updateScope.isBlank())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "DATA_ANALYSIS 必须通过数据分析任务接口修改");
        }
        List<Long> documentIds = normalizedDocumentIds(request.documentIds());
        validateReferences(current.getWorkspaceId(), request.connectionId(), documentIds);

        current.setConnectionId(request.connectionId());
        current.setModuleType(requestedModuleType);
        current.setTitle(normalizeRequiredText(request.title(), "title", 120));
        current.setDescription(normalizeRequiredText(request.description(), "description", 2000));
        current.setExpectedArtifactType(request.expectedArtifactType().trim().toUpperCase());
        current.setPriority(request.priority().trim().toUpperCase());
        current.setUpdatedAt(Instant.now());
        if (taskMapper.updateEditable(current) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "任务状态已变化，请刷新后重试");
        }
        replaceDocuments(id, documentIds);
        return toResponse(taskMapper.findById(id));
    }

    @Transactional(readOnly = true)
    public TaskRecord requireTask(Long id) {
        TaskRecord task = taskMapper.findById(id);
        if (task == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "任务不存在");
        }
        return task;
    }

    @Transactional(readOnly = true)
    public List<Long> findDocumentIds(Long taskId) {
        return taskMapper.findDocumentIds(taskId);
    }

    @Transactional
    public TaskRecord lockForRun(Long taskId) {
        return requireTaskForUpdate(taskId);
    }

    @Transactional
    public void validateLockedRunInput(TaskRecord task) {
        validateLockedRunInput(task, task.getConnectionId());
    }

    @Transactional
    public void validateLockedRunInput(TaskRecord task, Long connectionId) {
        if (connectionId == null
                || taskMapper.lockUsableConnection(connectionId) == null) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "AI Connection 不存在、未启用或已删除");
        }
    }

    @Transactional
    public TaskRecord attachRun(TaskRecord lockedTask,
                                Long runId,
                                Set<String> allowedStatuses) {
        if (!allowedStatuses.contains(lockedTask.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "当前任务状态不允许启动运行");
        }
        if (taskMapper.updateRunState(
                lockedTask.getId(), runId, "RUNNING", lockedTask.getStatus()) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "任务状态已变化，请刷新后重试");
        }
        lockedTask.setCurrentRunId(runId);
        lockedTask.setCurrentRunStatus("QUEUED");
        lockedTask.setStatus("RUNNING");
        return lockedTask;
    }

    @Transactional
    public void updateRunStatus(Long taskId, Long runId, String status) {
        if (taskMapper.updateStatusForCurrentRun(taskId, runId, status) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "任务状态已变化，请刷新后重试");
        }
    }

    private TaskRecord requireTaskForUpdate(Long id) {
        TaskRecord task = taskMapper.findByIdForUpdate(id);
        if (task == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "任务不存在");
        }
        return task;
    }

    private void validateReferences(Long workspaceId,
                                    Long connectionId,
                                    List<Long> documentIds) {
        if (taskMapper.lockActiveWorkspace(workspaceId) == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "工作空间不存在");
        }
        if (taskMapper.lockUsableConnection(connectionId) == null) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "AI Connection 不存在、未启用或已删除");
        }
        if (!documentIds.isEmpty()
                && taskMapper.lockDocuments(workspaceId, documentIds).size() != documentIds.size()) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "存在不属于当前工作空间的资料");
        }
    }

    private void validateValues(String moduleType,
                                String priority,
                                String expectedArtifactType) {
        String normalizedModuleType = moduleType.trim().toUpperCase();
        String normalizedArtifactType = expectedArtifactType.trim().toUpperCase();
        if (!MODULE_TYPES.contains(normalizedModuleType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "moduleType 必须为 TECH_LEARNING 或 DATA_ANALYSIS");
        }
        if (!PRIORITIES.contains(priority.trim().toUpperCase())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "priority 必须为 LOW、NORMAL 或 HIGH");
        }
        Set<String> supportedArtifactTypes = "DATA_ANALYSIS".equals(normalizedModuleType)
                ? DATA_ANALYSIS_ARTIFACT_TYPES
                : TECH_LEARNING_ARTIFACT_TYPES;
        if (!supportedArtifactTypes.contains(normalizedArtifactType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "expectedArtifactType 与 moduleType 不匹配");
        }
    }

    private void replaceDocuments(Long taskId, List<Long> documentIds) {
        taskMapper.deleteTaskDocuments(taskId);
        for (Long documentId : documentIds) {
            taskMapper.insertTaskDocument(taskId, documentId, "SOURCE");
        }
    }

    private List<Long> normalizedDocumentIds(List<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return List.of();
        }
        for (Long documentId : documentIds) {
            if (documentId == null || documentId <= 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                        "documentIds 必须为正整数");
            }
        }
        return new LinkedHashSet<>(documentIds).stream().sorted().toList();
    }

    private TaskResponse toResponse(TaskRecord task) {
        return new TaskResponse(
                task.getId(),
                task.getWorkspaceId(),
                task.getConnectionId(),
                task.getModuleType(),
                task.getTitle(),
                task.getDescription(),
                task.getExpectedArtifactType(),
                task.getPriority(),
                task.getStatus(),
                task.getCurrentRunId(),
                task.getCurrentRunStatus(),
                taskMapper.findDocumentIds(task.getId()),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }

    private String normalizeIdempotencyKey(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 128) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "Idempotency-Key 长度不能超过 128 个字符");
        }
        return normalized;
    }

    private String requestFingerprint(Long workspaceId,
                                      Long connectionId,
                                      String moduleType,
                                      String title,
                                      String description,
                                      String expectedArtifactType,
                                      String priority,
                                      List<Long> documentIds,
                                      String idempotencyScope) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            appendFingerprintField(digest, workspaceId);
            appendFingerprintField(digest, connectionId);
            appendFingerprintField(digest, moduleType);
            appendFingerprintField(digest, title);
            appendFingerprintField(digest, description);
            appendFingerprintField(digest, expectedArtifactType);
            appendFingerprintField(digest, priority);
            appendFingerprintField(digest, documentIds);
            appendFingerprintField(digest, idempotencyScope);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private void appendFingerprintField(MessageDigest digest, Object value) {
        String text = String.valueOf(value);
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.US_ASCII));
        digest.update((byte) ':');
        digest.update(bytes);
        digest.update((byte) '|');
    }

    private String normalizeRequiredText(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    fieldName + " 不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    fieldName + " 长度不能超过 " + maxLength);
        }
        return normalized;
    }
}
