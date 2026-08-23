package com.orbitworkbench.dataset.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.dataset.application.DatasetParsePersistenceService.StoredParsedSheet;
import com.orbitworkbench.dataset.application.DatasetParseResult.ParsedColumn;
import com.orbitworkbench.dataset.application.DatasetParseResult.ParsedSheet;
import com.orbitworkbench.dataset.domain.DatasetColumnRecord;
import com.orbitworkbench.dataset.domain.DatasetRecord;
import com.orbitworkbench.dataset.domain.DatasetSheetRecord;
import com.orbitworkbench.dataset.infrastructure.mapper.DatasetMapper;
import com.orbitworkbench.storage.application.LocalStorageService;
import com.orbitworkbench.storage.application.StorageCleanupAuditService;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DatasetParsePersistenceServiceTest {

    @Mock
    private DatasetMapper datasetMapper;
    @Mock
    private LocalStorageService storageService;
    @Mock
    private StorageCleanupAuditService cleanupAuditService;

    @Test
    void reparsePreservesEffectiveTypeOverrides() {
        DatasetRecord current = new DatasetRecord();
        current.setId(1L);
        current.setDocumentId(2L);
        current.setStatus("PARSING");
        current.setProfileVersion(1);
        when(datasetMapper.findByIdForUpdate(1L)).thenReturn(current);
        when(datasetMapper.findPreviewStorageRefs(1L)).thenReturn(List.of());
        when(datasetMapper.findProfileStorageRefs(1L)).thenReturn(List.of());

        DatasetSheetRecord oldSheet = new DatasetSheetRecord();
        oldSheet.setId(10L);
        oldSheet.setSheetIndex(0);
        when(datasetMapper.findSheets(1L)).thenReturn(List.of(oldSheet));

        DatasetColumnRecord oldColumn = new DatasetColumnRecord();
        oldColumn.setNormalizedName("revenue");
        oldColumn.setEffectiveType("STRING");
        when(datasetMapper.findColumns(1L, 10L)).thenReturn(List.of(oldColumn));

        AtomicLong sheetIds = new AtomicLong(20L);
        doAnswer(invocation -> {
            DatasetSheetRecord sheet = invocation.getArgument(0);
            sheet.setId(sheetIds.getAndIncrement());
            return null;
        }).when(datasetMapper).insertSheet(any(DatasetSheetRecord.class));
        when(datasetMapper.completeParsing(
                anyLong(), anyLong(), anyLong(), any(Integer.class),
                any(Integer.class), any())).thenReturn(1);

        ParsedColumn parsedColumn = new ParsedColumn(
                0, "revenue", "revenue", "DECIMAL", false,
                List.of("1550.00"));
        ParsedSheet parsedSheet = new ParsedSheet(
                0, "CSV", 1L, List.of(parsedColumn),
                List.of(List.of("1550.00")), Map.of(), Map.of());
        StoredParsedSheet storedSheet = new StoredParsedSheet(
                parsedSheet, "dataset-snapshots/preview", "dataset-snapshots/profile");
        DatasetParsePersistenceService service = new DatasetParsePersistenceService(
                datasetMapper, storageService, cleanupAuditService, new ObjectMapper());

        service.complete(1L, List.of(storedSheet));

        ArgumentCaptor<DatasetColumnRecord> columnCaptor =
                ArgumentCaptor.forClass(DatasetColumnRecord.class);
        verify(datasetMapper).insertColumn(columnCaptor.capture());
        assertEquals("DECIMAL", columnCaptor.getValue().getInferredType());
        assertEquals("STRING", columnCaptor.getValue().getEffectiveType());
    }
}
