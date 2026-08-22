package com.orbitworkbench.analysis.domain;

public class DataAnalysisRunContextRecord {

    private Long taskId;
    private Long workspaceId;
    private Long connectionId;
    private String taskTitle;
    private String taskStatus;
    private Long datasetId;
    private Long datasetWorkspaceId;
    private String datasetName;
    private String datasetFormat;
    private String datasetStatus;
    private Long sheetId;
    private String sheetName;
    private Long rowCount;
    private Integer columnCount;
    private String analysisGoal;
    private String expectedOutputsJson;
    private String columnOverridesJson;

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public Long getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(Long workspaceId) {
        this.workspaceId = workspaceId;
    }

    public Long getConnectionId() {
        return connectionId;
    }

    public void setConnectionId(Long connectionId) {
        this.connectionId = connectionId;
    }

    public String getTaskTitle() {
        return taskTitle;
    }

    public void setTaskTitle(String taskTitle) {
        this.taskTitle = taskTitle;
    }

    public String getTaskStatus() {
        return taskStatus;
    }

    public void setTaskStatus(String taskStatus) {
        this.taskStatus = taskStatus;
    }

    public Long getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(Long datasetId) {
        this.datasetId = datasetId;
    }

    public Long getDatasetWorkspaceId() {
        return datasetWorkspaceId;
    }

    public void setDatasetWorkspaceId(Long datasetWorkspaceId) {
        this.datasetWorkspaceId = datasetWorkspaceId;
    }

    public String getDatasetName() {
        return datasetName;
    }

    public void setDatasetName(String datasetName) {
        this.datasetName = datasetName;
    }

    public String getDatasetFormat() {
        return datasetFormat;
    }

    public void setDatasetFormat(String datasetFormat) {
        this.datasetFormat = datasetFormat;
    }

    public String getDatasetStatus() {
        return datasetStatus;
    }

    public void setDatasetStatus(String datasetStatus) {
        this.datasetStatus = datasetStatus;
    }

    public Long getSheetId() {
        return sheetId;
    }

    public void setSheetId(Long sheetId) {
        this.sheetId = sheetId;
    }

    public String getSheetName() {
        return sheetName;
    }

    public void setSheetName(String sheetName) {
        this.sheetName = sheetName;
    }

    public Long getRowCount() {
        return rowCount;
    }

    public void setRowCount(Long rowCount) {
        this.rowCount = rowCount;
    }

    public Integer getColumnCount() {
        return columnCount;
    }

    public void setColumnCount(Integer columnCount) {
        this.columnCount = columnCount;
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
}
