package com.orbitworkbench.analysis.infrastructure.mapper;

import com.orbitworkbench.analysis.domain.DataAnalysisRunContextRecord;
import com.orbitworkbench.analysis.domain.DataAnalysisTaskRecord;
import com.orbitworkbench.analysis.domain.DatasetSelectionRecord;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface DataAnalysisTaskMapper {

    void insert(DataAnalysisTaskRecord task);

    DataAnalysisTaskRecord findByTaskId(@Param("taskId") Long taskId);

    DataAnalysisRunContextRecord findRunContext(@Param("taskId") Long taskId);

    DatasetSelectionRecord lockDatasetSelection(@Param("datasetId") Long datasetId,
                                                @Param("sheetId") Long sheetId);

    List<Long> lockColumnIds(@Param("datasetId") Long datasetId,
                             @Param("sheetId") Long sheetId,
                             @Param("columnIds") List<Long> columnIds);

    int update(@Param("taskId") Long taskId,
               @Param("datasetId") Long datasetId,
               @Param("sheetId") Long sheetId,
               @Param("analysisGoal") String analysisGoal,
               @Param("expectedOutputsJson") String expectedOutputsJson,
               @Param("columnOverridesJson") String columnOverridesJson,
               @Param("updatedAt") Instant updatedAt);
}
