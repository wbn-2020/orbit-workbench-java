package com.orbitworkbench.analysis.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.analysis.api.DataAnalysisDtos.CreateDataAnalysisTaskRequest;
import com.orbitworkbench.analysis.api.DataAnalysisDtos.DataAnalysisSummaryResponse;
import com.orbitworkbench.analysis.api.DataAnalysisDtos.DataAnalysisTaskResponse;
import com.orbitworkbench.analysis.api.DataAnalysisDtos.UpdateDataAnalysisTaskRequest;
import com.orbitworkbench.analysis.domain.DataAnalysisRunContextRecord;
import com.orbitworkbench.analysis.domain.DataAnalysisTaskRecord;
import com.orbitworkbench.analysis.domain.DatasetSelectionRecord;
import com.orbitworkbench.analysis.infrastructure.mapper.DataAnalysisTaskMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.task.api.TaskDtos.CreateTaskRequest;
import com.orbitworkbench.task.api.TaskDtos.TaskResponse;
import com.orbitworkbench.task.api.TaskDtos.UpdateTaskRequest;
import com.orbitworkbench.task.application.TaskService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DataAnalysisTaskService {

    private static final String MODULE_TYPE = "DATA_ANALYSIS";
    private static final String PRIMARY_ARTIFACT_TYPE = "ANALYSIS_REPORT";
    private static final List<String> OUTPUT_ORDER =
            List.of("ANALYSIS_REPORT", "CHART_SPEC");
    private static final Set<String> COLUMN_TYPES =
            Set.of("EMPTY", "STRING", "INTEGER", "DECIMAL", "BOOLEAN", "DATETIME");
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<Long, String>> COLUMN_OVERRIDE_TYPE =
            new TypeReference<>() {
            };

    private final DataAnalysisTaskMapper mapper;
    private final TaskService taskService;
    private final ObjectMapper objectMapper;

    public DataAnalysisTaskService(DataAnalysisTaskMapper mapper,
                                   TaskService taskService,
                                   ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.taskService = taskService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DataAnalysisTaskResponse create(CreateDataAnalysisTaskRequest request,
                                           String idempotencyKey) {
        String normalizedKey = requireIdempotencyKey(idempotencyKey);
        NormalizedInput input = normalize(
                request.connectionId(),
                request.datasetId(),
                request.sheetId(),
                request.title(),
                request.analysisGoal(),
                request.expectedOutputs(),
                request.priority(),
                request.columnOverrides());

        TaskResponse task = taskService.create(
                new CreateTaskRequest(
                        request.workspaceId(),
                        MODULE_TYPE,
                        input.title(),
                        input.analysisGoal(),
                        PRIMARY_ARTIFACT_TYPE,
                        input.priority(),
                        List.of(),
                        input.connectionId()),
                normalizedKey,
                idempotencyScope(input));

        DataAnalysisTaskRecord existing = mapper.findByTaskId(task.id());
        if (existing == null) {
            validateDatasetSelection(
                    task.workspaceId(), input.datasetId(), input.sheetId(), input.columnOverrides());
            Instant now = Instant.now();
            DataAnalysisTaskRecord record = new DataAnalysisTaskRecord();
            record.setTaskId(task.id());
            record.setDatasetId(input.datasetId());
            record.setSheetId(input.sheetId());
            record.setAnalysisGoal(input.analysisGoal());
            record.setExpectedOutputsJson(writeJson(input.expectedOutputs()));
            record.setColumnOverridesJson(writeJson(input.columnOverrides()));
            record.setCreatedAt(now);
            record.setUpdatedAt(now);
            mapper.insert(record);
        }
        return response(task, requireContextRecord(task.id()));
    }

    @Transactional(readOnly = true)
    public DataAnalysisTaskResponse get(Long taskId) {
        TaskResponse task = requireDataAnalysisTask(taskId);
        return response(task, requireContextRecord(taskId));
    }

    @Transactional
    public DataAnalysisTaskResponse update(Long taskId,
                                           UpdateDataAnalysisTaskRequest request) {
        TaskResponse current = requireDataAnalysisTask(taskId);
        NormalizedInput input = normalize(
                request.connectionId(),
                request.datasetId(),
                request.sheetId(),
                request.title(),
                request.analysisGoal(),
                request.expectedOutputs(),
                request.priority(),
                request.columnOverrides());

        TaskResponse task = taskService.update(
                taskId,
                new UpdateTaskRequest(
                        MODULE_TYPE,
                        input.title(),
                        input.analysisGoal(),
                        PRIMARY_ARTIFACT_TYPE,
                        input.priority(),
                        List.of(),
                        input.connectionId()),
                MODULE_TYPE);
        validateDatasetSelection(
                current.workspaceId(), input.datasetId(), input.sheetId(), input.columnOverrides());
        if (mapper.update(
                taskId,
                input.datasetId(),
                input.sheetId(),
                input.analysisGoal(),
                writeJson(input.expectedOutputs()),
                writeJson(input.columnOverrides()),
                Instant.now()) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "数据分析任务扩展信息不存在");
        }
        return response(task, requireContextRecord(taskId));
    }

    @Transactional(readOnly = true)
    public DataAnalysisRunContext requireRunContext(Long taskId) {
        DataAnalysisRunContextRecord context = requireContextRecord(taskId);
        if (!"READY".equals(context.getDatasetStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.DATASET_NOT_READY,
                    "数据集尚未准备完成");
        }
        return new DataAnalysisRunContext(
                context.getTaskId(),
                context.getWorkspaceId(),
                context.getConnectionId(),
                context.getTaskTitle(),
                context.getDatasetId(),
                context.getDatasetName(),
                context.getDatasetFormat(),
                context.getSheetId(),
                context.getSheetName(),
                context.getRowCount(),
                context.getColumnCount(),
                context.getAnalysisGoal(),
                readExpectedOutputs(context.getExpectedOutputsJson()),
                readColumnOverrides(context.getColumnOverridesJson()));
    }

    private TaskResponse requireDataAnalysisTask(Long taskId) {
        TaskResponse task = taskService.get(taskId);
        if (!MODULE_TYPE.equals(task.moduleType())) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "数据分析任务不存在");
        }
        return task;
    }

    private DataAnalysisRunContextRecord requireContextRecord(Long taskId) {
        DataAnalysisRunContextRecord context = mapper.findRunContext(taskId);
        if (context == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "数据分析任务不存在");
        }
        if (!context.getWorkspaceId().equals(context.getDatasetWorkspaceId())) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "数据分析任务与数据集工作空间不一致");
        }
        return context;
    }

    private void validateDatasetSelection(Long workspaceId,
                                          Long datasetId,
                                          Long sheetId,
                                          Map<Long, String> columnOverrides) {
        DatasetSelectionRecord selection = mapper.lockDatasetSelection(datasetId, sheetId);
        if (selection == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "数据集不存在");
        }
        if (!workspaceId.equals(selection.getWorkspaceId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.DATASET_ACCESS_DENIED,
                    "数据集不属于当前工作空间");
        }
        if (!"READY".equals(selection.getDatasetStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.DATASET_NOT_READY,
                    "数据集尚未准备完成");
        }
        if (selection.getSheetId() == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "数据集工作表不存在");
        }
        if (!columnOverrides.isEmpty()) {
            List<Long> requestedIds = new ArrayList<>(columnOverrides.keySet());
            List<Long> matchedIds = mapper.lockColumnIds(datasetId, sheetId, requestedIds);
            if (matchedIds.size() != requestedIds.size()) {
                throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                        "字段类型覆盖包含不属于当前工作表的字段");
            }
        }
    }

    private NormalizedInput normalize(Long connectionId,
                                      Long datasetId,
                                      Long sheetId,
                                      String title,
                                      String analysisGoal,
                                      List<String> expectedOutputs,
                                      String priority,
                                      Map<Long, String> columnOverrides) {
        if (connectionId == null || connectionId <= 0
                || datasetId == null || datasetId <= 0
                || sheetId == null || sheetId <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "connectionId、datasetId 和 sheetId 必须为正整数");
        }
        String normalizedTitle = normalizeText(title, "title", 120);
        String normalizedGoal = normalizeText(analysisGoal, "analysisGoal", 2000);
        String normalizedPriority = normalizeText(priority, "priority", 16)
                .toUpperCase(Locale.ROOT);
        if (!Set.of("LOW", "NORMAL", "HIGH").contains(normalizedPriority)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "priority 必须为 LOW、NORMAL 或 HIGH");
        }
        return new NormalizedInput(
                connectionId,
                datasetId,
                sheetId,
                normalizedTitle,
                normalizedGoal,
                normalizeExpectedOutputs(expectedOutputs),
                normalizedPriority,
                normalizeColumnOverrides(columnOverrides));
    }

    private List<String> normalizeExpectedOutputs(List<String> expectedOutputs) {
        if (expectedOutputs == null || expectedOutputs.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "expectedOutputs 不能为空");
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String output : expectedOutputs) {
            String value = normalizeText(output, "expectedOutputs", 64)
                    .toUpperCase(Locale.ROOT);
            if (!OUTPUT_ORDER.contains(value)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                        "expectedOutputs 包含不受支持的成果类型");
            }
            normalized.add(value);
        }
        if (!normalized.contains(PRIMARY_ARTIFACT_TYPE)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "数据分析任务必须包含 ANALYSIS_REPORT");
        }
        return OUTPUT_ORDER.stream().filter(normalized::contains).toList();
    }

    private Map<Long, String> normalizeColumnOverrides(Map<Long, String> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return Map.of();
        }
        if (overrides.size() > 200) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "columnOverrides 不能超过 200 个字段");
        }
        Map<Long, String> normalized = new TreeMap<>();
        for (Map.Entry<Long, String> entry : overrides.entrySet()) {
            if (entry.getKey() == null || entry.getKey() <= 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                        "columnOverrides 字段 ID 必须为正整数");
            }
            String type = normalizeText(entry.getValue(), "columnOverrides", 32)
                    .toUpperCase(Locale.ROOT);
            if (!COLUMN_TYPES.contains(type)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                        "columnOverrides 包含不受支持的字段类型");
            }
            normalized.put(entry.getKey(), type);
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(normalized));
    }

    private String idempotencyScope(NormalizedInput input) {
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("datasetId", input.datasetId());
        scope.put("sheetId", input.sheetId());
        scope.put("analysisGoal", input.analysisGoal());
        scope.put("expectedOutputs", input.expectedOutputs());
        scope.put("columnOverrides", input.columnOverrides());
        return writeJson(scope);
    }

    private String requireIdempotencyKey(String value) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "Idempotency-Key 不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > 128) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "Idempotency-Key 长度不能超过 128 个字符");
        }
        return normalized;
    }

    private String normalizeText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    field + " 不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    field + " 长度不能超过 " + maxLength);
        }
        return normalized;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("数据分析任务 JSON 序列化失败", exception);
        }
    }

    private List<String> readExpectedOutputs(String value) {
        try {
            return List.copyOf(objectMapper.readValue(value, STRING_LIST_TYPE));
        } catch (JsonProcessingException | RuntimeException exception) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "数据分析任务期望输出已损坏");
        }
    }

    private Map<Long, String> readColumnOverrides(String value) {
        try {
            Map<Long, String> parsed = objectMapper.readValue(value, COLUMN_OVERRIDE_TYPE);
            return Collections.unmodifiableMap(new TreeMap<>(parsed));
        } catch (JsonProcessingException | RuntimeException exception) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "数据分析任务字段覆盖已损坏");
        }
    }

    private DataAnalysisTaskResponse response(TaskResponse task,
                                              DataAnalysisRunContextRecord context) {
        DataAnalysisSummaryResponse summary = new DataAnalysisSummaryResponse(
                context.getDatasetId(),
                context.getDatasetName(),
                context.getDatasetFormat(),
                context.getSheetId(),
                context.getSheetName(),
                context.getRowCount(),
                context.getColumnCount(),
                context.getAnalysisGoal(),
                readExpectedOutputs(context.getExpectedOutputsJson()),
                readColumnOverrides(context.getColumnOverridesJson()));
        return new DataAnalysisTaskResponse(
                task.id(),
                task.workspaceId(),
                task.connectionId(),
                task.moduleType(),
                task.title(),
                task.description(),
                task.expectedArtifactType(),
                task.priority(),
                task.status(),
                task.currentRunId(),
                task.currentRunStatus(),
                task.documentIds(),
                summary,
                task.createdAt(),
                task.updatedAt());
    }

    private record NormalizedInput(
            Long connectionId,
            Long datasetId,
            Long sheetId,
            String title,
            String analysisGoal,
            List<String> expectedOutputs,
            String priority,
            Map<Long, String> columnOverrides
    ) {
    }
}
