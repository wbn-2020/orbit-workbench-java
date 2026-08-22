package com.orbitworkbench.dataset.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.dataset.api.DatasetDtos.DatasetColumnResponse;
import com.orbitworkbench.dataset.api.DatasetDtos.DatasetDetailResponse;
import com.orbitworkbench.dataset.api.DatasetDtos.DatasetPreviewResponse;
import com.orbitworkbench.dataset.api.DatasetDtos.DatasetProfileResponse;
import com.orbitworkbench.dataset.api.DatasetDtos.DatasetSheetResponse;
import com.orbitworkbench.dataset.api.DatasetDtos.DatasetSummaryResponse;
import com.orbitworkbench.dataset.api.DatasetDtos.UpdateDatasetColumnRequest;
import com.orbitworkbench.dataset.domain.DatasetColumnRecord;
import com.orbitworkbench.dataset.domain.DatasetProfileRecord;
import com.orbitworkbench.dataset.domain.DatasetRecord;
import com.orbitworkbench.dataset.domain.DatasetSheetRecord;
import com.orbitworkbench.dataset.infrastructure.mapper.DatasetMapper;
import com.orbitworkbench.document.domain.DocumentRecord;
import com.orbitworkbench.document.infrastructure.mapper.DocumentMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.shared.api.PageResult;
import com.orbitworkbench.shared.config.DatasetProperties;
import com.orbitworkbench.storage.application.LocalStorageService;
import com.orbitworkbench.storage.application.StorageCleanupAuditService;
import com.orbitworkbench.storage.domain.StoredFile;
import com.orbitworkbench.storage.domain.TrashEntry;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DatasetService {

    private static final Set<String> DATASET_STATUSES =
            Set.of("UPLOADED", "PARSING", "READY", "FAILED");
    private static final Set<String> DATASET_FORMATS = Set.of("CSV", "XLSX");
    private static final Set<String> COLUMN_TYPES =
            Set.of("EMPTY", "STRING", "INTEGER", "DECIMAL", "BOOLEAN", "DATETIME");

    private final DatasetMapper datasetMapper;
    private final DocumentMapper documentMapper;
    private final LocalStorageService storageService;
    private final DatasetParseDispatcher parseDispatcher;
    private final DatasetProperties properties;
    private final ObjectMapper objectMapper;
    private final StorageCleanupAuditService cleanupAuditService;

    public DatasetService(DatasetMapper datasetMapper,
                          DocumentMapper documentMapper,
                          LocalStorageService storageService,
                          DatasetParseDispatcher parseDispatcher,
                          DatasetProperties properties,
                          ObjectMapper objectMapper,
                          StorageCleanupAuditService cleanupAuditService) {
        this.datasetMapper = datasetMapper;
        this.documentMapper = documentMapper;
        this.storageService = storageService;
        this.parseDispatcher = parseDispatcher;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.cleanupAuditService = cleanupAuditService;
    }

    @Transactional(readOnly = true)
    public PageResult<DatasetSummaryResponse> findPage(Long workspaceId,
                                                       String status,
                                                       String format,
                                                       String updatedFrom,
                                                       String updatedTo,
                                                       int page,
                                                       int size) {
        String normalizedStatus = optionalEnum(status, DATASET_STATUSES, "status");
        String normalizedFormat = optionalEnum(format, DATASET_FORMATS, "format");
        LocalDateTime normalizedUpdatedFrom = parseDateFilter(updatedFrom, "updatedFrom");
        LocalDateTime normalizedUpdatedTo = parseDateFilter(updatedTo, "updatedTo");
        LocalDateTime normalizedUpdatedToExclusive = normalizedUpdatedTo == null
                ? null : normalizedUpdatedTo.plusDays(1);
        if (normalizedUpdatedFrom != null
                && normalizedUpdatedToExclusive != null
                && !normalizedUpdatedFrom.isBefore(normalizedUpdatedToExclusive)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "updatedFrom 不能晚于 updatedTo");
        }
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        long offset = ((long) normalizedPage - 1) * normalizedSize;
        List<DatasetSummaryResponse> items = datasetMapper.findPage(
                        workspaceId,
                        normalizedStatus,
                        normalizedFormat,
                        normalizedUpdatedFrom,
                        normalizedUpdatedToExclusive,
                        offset,
                        normalizedSize)
                .stream()
                .map(this::toSummary)
                .toList();
        return new PageResult<>(
                items,
                normalizedPage,
                normalizedSize,
                datasetMapper.countPage(
                        workspaceId,
                        normalizedStatus,
                        normalizedFormat,
                        normalizedUpdatedFrom,
                        normalizedUpdatedToExclusive));
    }

    @Transactional(readOnly = true)
    public DatasetDetailResponse get(Long id) {
        DatasetRecord dataset = requireDataset(id);
        return toDetail(dataset, datasetMapper.findSheets(id));
    }

    @Transactional
    public DatasetSummaryResponse upload(Long workspaceId,
                                         String requestedName,
                                         MultipartFile file) {
        validateUpload(workspaceId, file);
        String originalName = normalizedFileName(file.getOriginalFilename());
        String format = formatFor(originalName);
        validateMediaType(format, file.getContentType());

        StoredFile stored;
        try (InputStream input = file.getInputStream()) {
            stored = storageService.storeDataset(input, properties.getMaxFileBytes());
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.FILE_NOT_FOUND,
                    "无法读取上传文件");
        }

        boolean rollbackCleanupRegistered = registerRollbackCleanup(stored);
        try {
            if (documentMapper.lockActiveWorkspace(workspaceId) == null) {
                throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                        "工作空间不存在或不可用");
            }
            if (datasetMapper.findByWorkspaceAndHash(workspaceId, stored.contentHash()) != null) {
                throw duplicateDataset();
            }
            DatasetRecord deleted = datasetMapper.findDeletedByWorkspaceAndHashForUpdate(
                    workspaceId, stored.contentHash());
            if (deleted != null) {
                return reactivateDeletedDataset(
                        deleted,
                        requestedName,
                        originalName,
                        format,
                        stored);
            }

            Instant now = Instant.now();
            DocumentRecord document = new DocumentRecord();
            document.setWorkspaceId(workspaceId);
            document.setName(originalName);
            document.setMediaType(mediaType(format));
            document.setSizeBytes(stored.sizeBytes());
            document.setStorageRef(stored.storageRef());
            document.setContentHash(stored.contentHash());
            document.setParseStatus("UPLOADED");
            document.setCreatedAt(now);
            document.setUpdatedAt(now);
            documentMapper.insert(document);

            DatasetRecord dataset = new DatasetRecord();
            dataset.setWorkspaceId(workspaceId);
            dataset.setDocumentId(document.getId());
            dataset.setName(normalizedDatasetName(requestedName, originalName));
            dataset.setFormat(format);
            dataset.setStatus("UPLOADED");
            dataset.setContentHash(stored.contentHash());
            dataset.setProfileVersion(0);
            dataset.setVersion(1L);
            dataset.setCreatedAt(now);
            dataset.setUpdatedAt(now);
            datasetMapper.insertDataset(dataset);

            registerAfterCommit(() -> parseDispatcher.submit(dataset.getId(), false));
            return toSummary(dataset);
        } catch (DuplicateKeyException exception) {
            if (!rollbackCleanupRegistered) {
                moveToTrash(stored);
            }
            throw duplicateDataset();
        } catch (RuntimeException exception) {
            if (!rollbackCleanupRegistered) {
                moveToTrash(stored);
            }
            throw exception;
        }
    }

    @Transactional
    public DatasetSummaryResponse queueParse(Long id, boolean reparse) {
        DatasetRecord dataset = datasetMapper.findByIdForUpdate(id);
        if (dataset == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "数据集不存在");
        }
        if ("PARSING".equals(dataset.getStatus())) {
            return toSummary(dataset);
        }
        if (!reparse && "READY".equals(dataset.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "数据集已经解析完成");
        }
        Instant now = Instant.now();
        if (datasetMapper.markParsing(id, now) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "数据集状态已变化");
        }
        datasetMapper.updateDocumentStatus(dataset.getDocumentId(), "PARSING", now);
        dataset.setStatus("PARSING");
        dataset.setErrorCode(null);
        dataset.setErrorSummary(null);
        dataset.setUpdatedAt(now);
        registerAfterCommit(() -> parseDispatcher.submit(id, true));
        return toSummary(dataset);
    }

    @Transactional(readOnly = true)
    public List<DatasetSheetResponse> sheets(Long datasetId) {
        requireDataset(datasetId);
        return datasetMapper.findSheets(datasetId).stream()
                .map(this::toSheet)
                .toList();
    }

    @Transactional(readOnly = true)
    public DatasetSheetResponse sheet(Long datasetId, Long sheetId) {
        requireDataset(datasetId);
        return toSheet(requireSheet(datasetId, sheetId));
    }

    @Transactional(readOnly = true)
    public List<DatasetColumnResponse> columns(Long datasetId, Long sheetId) {
        requireReadyDataset(datasetId);
        requireSheet(datasetId, sheetId);
        return datasetMapper.findColumns(datasetId, sheetId).stream()
                .map(this::toColumn)
                .toList();
    }

    @Transactional(readOnly = true)
    public DatasetPreviewResponse preview(Long datasetId,
                                          Long sheetId,
                                          int offset,
                                          int limit) {
        requireReadyDataset(datasetId);
        DatasetSheetRecord sheet = requireSheet(datasetId, sheetId);
        PreviewSnapshot snapshot = readPreview(sheet.getPreviewStorageRef());
        int normalizedOffset = Math.max(offset, 0);
        int normalizedLimit = Math.min(Math.max(limit, 1), properties.getMaxPreviewRows());
        int from = Math.min(normalizedOffset, snapshot.rows().size());
        int to = Math.min(from + normalizedLimit, snapshot.rows().size());
        return new DatasetPreviewResponse(
                snapshot.columns(),
                snapshot.rows().subList(from, to),
                normalizedOffset,
                normalizedLimit,
                to < snapshot.rows().size(),
                snapshot.totalRows());
    }

    @Transactional(readOnly = true)
    public DatasetProfileResponse profile(Long datasetId, Long sheetId) {
        requireReadyDataset(datasetId);
        requireSheet(datasetId, sheetId);
        DatasetProfileRecord profile = datasetMapper.findLatestProfile(datasetId, sheetId);
        if (profile == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "数据集 Profile 不存在");
        }
        return new DatasetProfileResponse(
                datasetId,
                sheetId,
                profile.getProfileVersion(),
                profile.getRowCount(),
                profile.getColumnCount(),
                readMap(profile.getSummaryJson()),
                readMap(profile.getQualityJson()),
                profile.getCreatedAt());
    }

    @Transactional
    public DatasetColumnResponse updateColumn(Long datasetId,
                                              Long sheetId,
                                              Long columnId,
                                              UpdateDatasetColumnRequest request) {
        requireReadyDataset(datasetId);
        requireSheet(datasetId, sheetId);
        DatasetColumnRecord column =
                datasetMapper.findColumnForUpdate(datasetId, sheetId, columnId);
        if (column == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "数据字段不存在");
        }
        String effectiveType = request.effectiveType().trim().toUpperCase(Locale.ROOT);
        if (!COLUMN_TYPES.contains(effectiveType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "effectiveType 不受支持");
        }
        if (!request.expectedVersion().equals(column.getVersion())) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "字段版本已变化，请刷新后重试");
        }
        Instant now = Instant.now();
        if (datasetMapper.updateColumnType(
                columnId, request.expectedVersion(), effectiveType, now) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "字段版本已变化，请刷新后重试");
        }
        column.setEffectiveType(effectiveType);
        column.setVersion(column.getVersion() + 1);
        column.setUpdatedAt(now);
        return toColumn(column);
    }

    @Transactional
    public void delete(Long datasetId) {
        DatasetRecord dataset = datasetMapper.findByIdForUpdate(datasetId);
        if (dataset == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "数据集不存在");
        }
        if ("PARSING".equals(dataset.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "数据集正在解析，不能删除");
        }
        if (datasetMapper.countAnalysisReferences(datasetId) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "数据集仍被分析任务引用，不能删除");
        }

        List<String> refs = new ArrayList<>(datasetMapper.findSnapshotStorageRefs(datasetId));
        refs.add(dataset.getDocumentStorageRef());
        List<TrashEntry> trashEntries = new ArrayList<>();
        try {
            for (String ref : refs) {
                trashEntries.add(storageService.moveToTrash(ref));
            }
            if (datasetMapper.softDelete(datasetId, Instant.now()) != 1) {
                throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                        "数据集状态已变化");
            }
            registerRollbackRestore(trashEntries);
        } catch (RuntimeException exception) {
            restoreAll(trashEntries, exception);
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public DatasetRecord requireReadyDataset(Long datasetId) {
        DatasetRecord dataset = requireDataset(datasetId);
        if (!"READY".equals(dataset.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.DATASET_NOT_READY,
                    "数据集尚未解析完成");
        }
        return dataset;
    }

    @Transactional(readOnly = true)
    public DatasetRecord requireDataset(Long id) {
        DatasetRecord dataset = datasetMapper.findById(id);
        if (dataset == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "数据集不存在");
        }
        return dataset;
    }

    private DatasetSheetRecord requireSheet(Long datasetId, Long sheetId) {
        DatasetSheetRecord sheet = datasetMapper.findSheet(datasetId, sheetId);
        if (sheet == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "数据集工作表不存在");
        }
        return sheet;
    }

    private void validateUpload(Long workspaceId, MultipartFile file) {
        if (workspaceId == null || workspaceId <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "workspaceId 必须为正整数");
        }
        if (file == null || file.isEmpty() || file.getOriginalFilename() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "上传文件不能为空");
        }
        if (file.getSize() > properties.getMaxFileBytes()) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE,
                    ErrorCode.DATASET_TOO_LARGE,
                    "数据文件超过大小限制");
        }
    }

    private String normalizedFileName(String value) {
        String normalized = value == null ? "" : value.replace('\\', '/');
        int separator = normalized.lastIndexOf('/');
        String name = normalized.substring(separator + 1).trim();
        if (name.isEmpty() || name.length() > 255 || name.indexOf('\0') >= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "文件名长度必须为 1-255");
        }
        return name;
    }

    private String normalizedDatasetName(String requestedName, String fileName) {
        String name = requestedName == null || requestedName.isBlank()
                ? fileName : requestedName.trim();
        if (name.length() > 255) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "数据集名称长度不能超过 255");
        }
        return name;
    }

    private String formatFor(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".csv")) {
            return "CSV";
        }
        if (lower.endsWith(".xlsx")) {
            return "XLSX";
        }
        throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                ErrorCode.DATASET_UNSUPPORTED_FORMAT,
                "仅支持 CSV 和 XLSX 文件");
    }

    private void validateMediaType(String format, String mediaType) {
        if (mediaType == null || mediaType.isBlank()
                || "application/octet-stream".equalsIgnoreCase(mediaType)) {
            return;
        }
        String normalized = mediaType.toLowerCase(Locale.ROOT);
        boolean valid = "CSV".equals(format)
                ? normalized.equals("text/csv")
                || normalized.equals("application/csv")
                || normalized.equals("text/plain")
                : normalized.equals(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        if (!valid) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    ErrorCode.DATASET_UNSUPPORTED_FORMAT,
                    "文件媒体类型与扩展名不一致");
        }
    }

    private String mediaType(String format) {
        return "CSV".equals(format)
                ? "text/csv"
                : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    }

    private String optionalEnum(String value, Set<String> allowed, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    field + " 不受支持");
        }
        return normalized;
    }

    private LocalDateTime parseDateFilter(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim()).atStartOfDay();
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    field + " 必须为 YYYY-MM-DD");
        }
    }

    private PreviewSnapshot readPreview(String storageRef) {
        try {
            return objectMapper.readValue(
                    storageService.readUtf8(storageRef),
                    PreviewSnapshot.class);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    ErrorCode.DATASET_PARSE_FAILED,
                    "数据集预览无法读取");
        }
    }

    private Map<String, Object> readMap(String json) {
        try {
            return objectMapper.readValue(
                    json,
                    new TypeReference<>() {
                    });
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    ErrorCode.DATASET_PARSE_FAILED,
                    "数据集 Profile 无法读取");
        }
    }

    private DatasetSummaryResponse toSummary(DatasetRecord dataset) {
        return new DatasetSummaryResponse(
                dataset.getId(),
                dataset.getWorkspaceId(),
                dataset.getDocumentId(),
                dataset.getName(),
                dataset.getFormat(),
                dataset.getStatus(),
                dataset.getActiveSheetId(),
                dataset.getActiveSheetName(),
                dataset.getRowCount(),
                dataset.getColumnCount(),
                dataset.getErrorCode(),
                dataset.getErrorSummary(),
                dataset.getCreatedAt(),
                dataset.getUpdatedAt());
    }

    private DatasetDetailResponse toDetail(DatasetRecord dataset,
                                           List<DatasetSheetRecord> sheets) {
        return new DatasetDetailResponse(
                dataset.getId(),
                dataset.getWorkspaceId(),
                dataset.getDocumentId(),
                dataset.getName(),
                dataset.getFormat(),
                dataset.getStatus(),
                dataset.getActiveSheetId(),
                dataset.getRowCount(),
                dataset.getColumnCount(),
                dataset.getProfileVersion(),
                dataset.getVersion(),
                dataset.getErrorCode(),
                dataset.getErrorSummary(),
                sheets.stream().map(this::toSheet).toList(),
                dataset.getCreatedAt(),
                dataset.getUpdatedAt());
    }

    private DatasetSheetResponse toSheet(DatasetSheetRecord sheet) {
        return new DatasetSheetResponse(
                sheet.getId(),
                sheet.getDatasetId(),
                sheet.getSheetIndex(),
                sheet.getSheetName(),
                sheet.getRowCount(),
                sheet.getColumnCount(),
                sheet.getCreatedAt(),
                sheet.getUpdatedAt());
    }

    private DatasetColumnResponse toColumn(DatasetColumnRecord column) {
        return new DatasetColumnResponse(
                column.getId(),
                column.getDatasetId(),
                column.getSheetId(),
                column.getOrdinalPosition(),
                column.getColumnName(),
                column.getNormalizedName(),
                column.getInferredType(),
                column.getEffectiveType(),
                column.isNullable(),
                readStringList(column.getSampleValuesJson()),
                column.getVersion(),
                column.getCreatedAt(),
                column.getUpdatedAt());
    }

    private List<String> readStringList(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private ApiException duplicateDataset() {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.DUPLICATE_REQUEST,
                "当前工作空间已存在内容相同的数据集");
    }

    private boolean registerRollbackCleanup(StoredFile stored) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return false;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    moveToTrash(stored);
                }
            }
        });
        return true;
    }

    private void registerAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private void registerRollbackRestore(List<TrashEntry> entries) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    restoreAll(entries, null);
                }
            }
        });
    }

    private void moveToTrash(StoredFile stored) {
        try {
            storageService.moveToTrash(stored.storageRef());
        } catch (RuntimeException exception) {
            cleanupAuditService.record(
                    "DATASET_UPLOAD", null, stored.storageRef(), exception);
        }
    }

    private DatasetSummaryResponse reactivateDeletedDataset(
            DatasetRecord dataset,
            String requestedName,
            String originalName,
            String format,
            StoredFile stored) {
        Instant now = Instant.now();
        if (datasetMapper.prepareReactivation(dataset.getId(), now) != 1) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.STATE_CONFLICT,
                    "已删除数据集状态发生变化");
        }
        datasetMapper.deleteProfiles(dataset.getId());
        datasetMapper.deleteColumns(dataset.getId());
        datasetMapper.deleteSheets(dataset.getId());
        if (datasetMapper.reactivateDocument(
                dataset.getDocumentId(),
                originalName,
                mediaType(format),
                stored.sizeBytes(),
                stored.storageRef(),
                stored.contentHash(),
                now) != 1) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.STATE_CONFLICT,
                    "数据集原文件记录无法恢复");
        }
        String name = normalizedDatasetName(requestedName, originalName);
        if (datasetMapper.reactivateDataset(
                dataset.getId(), name, format, now) != 1) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.STATE_CONFLICT,
                    "已删除数据集无法恢复");
        }
        dataset.setName(name);
        dataset.setFormat(format);
        dataset.setStatus("UPLOADED");
        dataset.setActiveSheetId(null);
        dataset.setActiveSheetName(null);
        dataset.setRowCount(null);
        dataset.setColumnCount(null);
        dataset.setErrorCode(null);
        dataset.setErrorSummary(null);
        dataset.setDeletedAt(null);
        dataset.setUpdatedAt(now);
        registerAfterCommit(() -> parseDispatcher.submit(dataset.getId(), false));
        return toSummary(dataset);
    }

    private void restoreAll(List<TrashEntry> entries, RuntimeException cause) {
        for (int index = entries.size() - 1; index >= 0; index--) {
            try {
                storageService.restore(entries.get(index));
            } catch (RuntimeException restoreException) {
                if (cause != null) {
                    cause.addSuppressed(restoreException);
                }
            }
        }
    }

    private record PreviewSnapshot(
            List<String> columns,
            List<List<String>> rows,
            long totalRows
    ) {
    }
}
