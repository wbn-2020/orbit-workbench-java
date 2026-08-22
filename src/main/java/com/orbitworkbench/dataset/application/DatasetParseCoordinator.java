package com.orbitworkbench.dataset.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.dataset.application.DatasetParsePersistenceService.StoredParsedSheet;
import com.orbitworkbench.dataset.application.DatasetParseResult.ParsedSheet;
import com.orbitworkbench.dataset.domain.DatasetRecord;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.shared.config.DatasetProperties;
import com.orbitworkbench.storage.application.LocalStorageService;
import com.orbitworkbench.storage.application.StorageCleanupAuditService;
import com.orbitworkbench.storage.domain.StoredFile;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class DatasetParseCoordinator {

    private final DatasetParsePersistenceService persistenceService;
    private final DatasetFileParserService parserService;
    private final LocalStorageService storageService;
    private final ObjectMapper objectMapper;
    private final DatasetProperties properties;
    private final StorageCleanupAuditService cleanupAuditService;

    public DatasetParseCoordinator(DatasetParsePersistenceService persistenceService,
                                   DatasetFileParserService parserService,
                                   LocalStorageService storageService,
                                   ObjectMapper objectMapper,
                                   DatasetProperties properties,
                                   StorageCleanupAuditService cleanupAuditService) {
        this.persistenceService = persistenceService;
        this.parserService = parserService;
        this.storageService = storageService;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.cleanupAuditService = cleanupAuditService;
    }

    public void execute(Long datasetId, boolean alreadyMarked) {
        DatasetRecord dataset = alreadyMarked
                ? persistenceService.requireMarked(datasetId)
                : persistenceService.begin(datasetId);
        if (dataset == null) {
            return;
        }

        List<SnapshotFile> newSnapshots = new ArrayList<>();
        try (InputStream input = storageService.open(dataset.getDocumentStorageRef())) {
            DatasetParseResult parsed = parserService.parse(dataset.getFormat(), input);
            List<StoredParsedSheet> storedSheets = new ArrayList<>(parsed.sheets().size());
            for (ParsedSheet sheet : parsed.sheets()) {
                Map<String, Object> preview = new LinkedHashMap<>();
                preview.put("columns", sheet.columns().stream()
                        .map(DatasetParseResult.ParsedColumn::columnName)
                        .toList());
                preview.put("rows", sheet.previewRows());
                preview.put("totalRows", sheet.rowCount());
                StoredFile previewFile = storeSnapshot(preview);
                newSnapshots.add(new SnapshotFile(
                        "DATASET_PREVIEW", previewFile));

                Map<String, Object> profile = new LinkedHashMap<>();
                profile.put("summary", sheet.summary());
                profile.put("quality", sheet.quality());
                StoredFile profileFile = storeSnapshot(profile);
                newSnapshots.add(new SnapshotFile(
                        "DATASET_PROFILE", profileFile));

                storedSheets.add(new StoredParsedSheet(
                        sheet,
                        previewFile.storageRef(),
                        profileFile.storageRef()));
            }
            persistenceService.complete(datasetId, storedSheets);
        } catch (Throwable exception) {
            cleanupSnapshots(datasetId, newSnapshots);
            ErrorCode code = errorCode(exception);
            persistenceService.fail(datasetId, code, sanitizedSummary(exception));
        }
    }

    private StoredFile storeSnapshot(Object value) throws Exception {
        byte[] bytes = objectMapper.writeValueAsBytes(value);
        if (bytes.length > properties.getMaxFileBytes()) {
            throw new DatasetParseException(
                    ErrorCode.DATASET_TOO_LARGE,
                    "数据集预览或摘要超过存储限制");
        }
        return storageService.storeDatasetSnapshot(
                new ByteArrayInputStream(bytes),
                properties.getMaxFileBytes());
    }

    private void cleanupSnapshots(Long datasetId, List<SnapshotFile> snapshots) {
        for (SnapshotFile snapshot : snapshots) {
            try {
                storageService.moveToTrash(snapshot.file().storageRef());
            } catch (RuntimeException exception) {
                cleanupAuditService.record(
                        snapshot.resourceType(), datasetId,
                        snapshot.file().storageRef(), exception);
            }
        }
    }

    private record SnapshotFile(String resourceType, StoredFile file) {
    }

    private ErrorCode errorCode(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof DatasetParseException parseException) {
                return parseException.getErrorCode();
            }
            current = current.getCause();
        }
        return ErrorCode.DATASET_PARSE_FAILED;
    }

    private String sanitizedSummary(Throwable exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = "数据文件无法解析";
        }
        String normalized = message.replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .trim();
        return normalized.length() <= 512 ? normalized : normalized.substring(0, 512);
    }
}
