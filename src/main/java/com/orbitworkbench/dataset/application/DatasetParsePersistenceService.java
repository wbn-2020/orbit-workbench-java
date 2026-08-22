package com.orbitworkbench.dataset.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.dataset.application.DatasetParseResult.ParsedSheet;
import com.orbitworkbench.dataset.domain.DatasetColumnRecord;
import com.orbitworkbench.dataset.domain.DatasetProfileRecord;
import com.orbitworkbench.dataset.domain.DatasetRecord;
import com.orbitworkbench.dataset.domain.DatasetSheetRecord;
import com.orbitworkbench.dataset.infrastructure.mapper.DatasetMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.storage.application.LocalStorageService;
import com.orbitworkbench.storage.application.StorageCleanupAuditService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class DatasetParsePersistenceService {

    private final DatasetMapper datasetMapper;
    private final LocalStorageService storageService;
    private final StorageCleanupAuditService cleanupAuditService;
    private final ObjectMapper objectMapper;

    public DatasetParsePersistenceService(DatasetMapper datasetMapper,
                                          LocalStorageService storageService,
                                          StorageCleanupAuditService cleanupAuditService,
                                          ObjectMapper objectMapper) {
        this.datasetMapper = datasetMapper;
        this.storageService = storageService;
        this.cleanupAuditService = cleanupAuditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DatasetRecord begin(Long datasetId) {
        DatasetRecord current = datasetMapper.findByIdForUpdate(datasetId);
        if (current == null) {
            return null;
        }
        if ("PARSING".equals(current.getStatus())) {
            return null;
        }
        if (!List.of("UPLOADED", "READY", "FAILED").contains(current.getStatus())) {
            return null;
        }
        Instant now = Instant.now();
        if (datasetMapper.markParsing(datasetId, now) != 1) {
            return null;
        }
        datasetMapper.updateDocumentStatus(current.getDocumentId(), "PARSING", now);
        current.setStatus("PARSING");
        current.setUpdatedAt(now);
        return current;
    }

    @Transactional
    public DatasetRecord requireMarked(Long datasetId) {
        DatasetRecord current = datasetMapper.findByIdForUpdate(datasetId);
        if (current == null || !"PARSING".equals(current.getStatus())) {
            return null;
        }
        return current;
    }

    @Transactional
    public void complete(Long datasetId, List<StoredParsedSheet> storedSheets) {
        DatasetRecord current = datasetMapper.findByIdForUpdate(datasetId);
        if (current == null || !"PARSING".equals(current.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "数据集解析状态已变化");
        }
        if (storedSheets == null || storedSheets.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.DATASET_PARSE_FAILED,
                    "数据集没有可保存的工作表");
        }

        List<String> oldPreviewRefs =
                new ArrayList<>(datasetMapper.findPreviewStorageRefs(datasetId));
        List<String> oldProfileRefs =
                new ArrayList<>(datasetMapper.findProfileStorageRefs(datasetId));
        Instant now = Instant.now();
        datasetMapper.clearActiveSheet(datasetId, now);
        datasetMapper.deleteProfiles(datasetId);
        datasetMapper.deleteColumns(datasetId);
        datasetMapper.deleteSheets(datasetId);

        Long activeSheetId = null;
        long activeRowCount = 0;
        int activeColumnCount = 0;
        int profileVersion = Math.max(current.getProfileVersion() == null
                ? 0 : current.getProfileVersion(), 0) + 1;

        for (StoredParsedSheet stored : storedSheets) {
            ParsedSheet parsed = stored.parsed();
            DatasetSheetRecord sheet = new DatasetSheetRecord();
            sheet.setDatasetId(datasetId);
            sheet.setSheetIndex(parsed.sheetIndex());
            sheet.setSheetName(parsed.sheetName());
            sheet.setRowCount(parsed.rowCount());
            sheet.setColumnCount(parsed.columns().size());
            sheet.setPreviewStorageRef(stored.previewStorageRef());
            sheet.setCreatedAt(now);
            sheet.setUpdatedAt(now);
            datasetMapper.insertSheet(sheet);

            if (activeSheetId == null) {
                activeSheetId = sheet.getId();
                activeRowCount = parsed.rowCount();
                activeColumnCount = parsed.columns().size();
            }

            parsed.columns().forEach(parsedColumn -> {
                DatasetColumnRecord column = new DatasetColumnRecord();
                column.setDatasetId(datasetId);
                column.setSheetId(sheet.getId());
                column.setOrdinalPosition(parsedColumn.ordinalPosition());
                column.setColumnName(parsedColumn.columnName());
                column.setNormalizedName(parsedColumn.normalizedName());
                column.setInferredType(parsedColumn.inferredType());
                column.setEffectiveType(parsedColumn.inferredType());
                column.setNullable(parsedColumn.nullable());
                column.setSampleValuesJson(writeJson(parsedColumn.sampleValues()));
                column.setVersion(1L);
                column.setCreatedAt(now);
                column.setUpdatedAt(now);
                datasetMapper.insertColumn(column);
            });

            DatasetProfileRecord profile = new DatasetProfileRecord();
            profile.setDatasetId(datasetId);
            profile.setSheetId(sheet.getId());
            profile.setProfileVersion(profileVersion);
            profile.setRowCount(parsed.rowCount());
            profile.setColumnCount(parsed.columns().size());
            profile.setSummaryJson(writeJson(parsed.summary()));
            profile.setQualityJson(writeJson(parsed.quality()));
            profile.setProfileStorageRef(stored.profileStorageRef());
            profile.setCreatedAt(now);
            datasetMapper.insertProfile(profile);
        }

        if (datasetMapper.completeParsing(
                datasetId,
                activeSheetId,
                activeRowCount,
                activeColumnCount,
                profileVersion,
                now) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "数据集解析状态已变化");
        }
        datasetMapper.updateDocumentStatus(current.getDocumentId(), "READY", now);
        registerAfterCommitCleanup(
                datasetId, "DATASET_PREVIEW", oldPreviewRefs);
        registerAfterCommitCleanup(
                datasetId, "DATASET_PROFILE", oldProfileRefs);
    }

    @Transactional
    public void fail(Long datasetId, ErrorCode errorCode, String summary) {
        DatasetRecord current = datasetMapper.findByIdForUpdate(datasetId);
        if (current == null || !"PARSING".equals(current.getStatus())) {
            return;
        }
        Instant now = Instant.now();
        datasetMapper.failParsing(datasetId, errorCode.name(), summary, now);
        datasetMapper.updateDocumentStatus(current.getDocumentId(), "FAILED", now);
    }

    @Transactional
    public void recoverInterrupted() {
        for (Long datasetId : datasetMapper.findParsingIds()) {
            DatasetRecord current = datasetMapper.findByIdForUpdate(datasetId);
            if (current == null || !"PARSING".equals(current.getStatus())) {
                continue;
            }
            Instant now = Instant.now();
            datasetMapper.failParsing(
                    datasetId,
                    ErrorCode.STREAM_INTERRUPTED.name(),
                    "服务重启导致数据集解析中断，请重新解析",
                    now);
            datasetMapper.updateDocumentStatus(current.getDocumentId(), "FAILED", now);
        }
    }

    private void registerAfterCommitCleanup(Long datasetId,
                                            String resourceType,
                                            List<String> storageRefs) {
        if (storageRefs == null || storageRefs.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            moveToTrash(datasetId, resourceType, storageRefs);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                moveToTrash(datasetId, resourceType, storageRefs);
            }
        });
    }

    private void moveToTrash(Long datasetId,
                             String resourceType,
                             List<String> storageRefs) {
        for (String storageRef : storageRefs) {
            try {
                storageService.moveToTrash(storageRef);
            } catch (RuntimeException exception) {
                cleanupAuditService.record(
                        resourceType, datasetId, storageRef, exception);
            }
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    ErrorCode.DATASET_PARSE_FAILED,
                    "数据集摘要无法序列化");
        }
    }

    public record StoredParsedSheet(
            ParsedSheet parsed,
            String previewStorageRef,
            String profileStorageRef
    ) {
    }
}
