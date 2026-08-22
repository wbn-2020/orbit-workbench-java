package com.orbitworkbench.analysis.domain;

import java.time.Instant;

public class DataAnalysisTaskRecord {

    private Long taskId;
    private Long datasetId;
    private Long sheetId;
    private String analysisGoal;
    private String expectedOutputsJson;
    private String columnOverridesJson;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public Long getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(Long datasetId) {
        this.datasetId = datasetId;
    }

    public Long getSheetId() {
        return sheetId;
    }

    public void setSheetId(Long sheetId) {
        this.sheetId = sheetId;
    }

    public String getAnalysisGoal() {
        return analysisGoal;
    }

    public void setAnalysisGoal(String analysisGoal) {
        this.analysisGoal = analysisGoal;
    }

    public String getExpectedOutputsJson() {
        return expectedOutputsJson;
    }

    public void setExpectedOutputsJson(String expectedOutputsJson) {
        this.expectedOutputsJson = expectedOutputsJson;
    }

    public String getColumnOverridesJson() {
        return columnOverridesJson;
    }

    public void setColumnOverridesJson(String columnOverridesJson) {
        this.columnOverridesJson = columnOverridesJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
