package com.orbitworkbench.analysis.domain;

public class DatasetSelectionRecord {

    private Long datasetId;
    private Long workspaceId;
    private String datasetName;
    private String datasetFormat;
    private String datasetStatus;
    private Long datasetRowCount;
    private Integer datasetColumnCount;
    private Long sheetId;
    private String sheetName;
    private Long sheetRowCount;
    private Integer sheetColumnCount;

    public Long getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(Long datasetId) {
        this.datasetId = datasetId;
    }

    public Long getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(Long workspaceId) {
        this.workspaceId = workspaceId;
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

    public Long getDatasetRowCount() {
        return datasetRowCount;
    }

    public void setDatasetRowCount(Long datasetRowCount) {
        this.datasetRowCount = datasetRowCount;
    }

    public Integer getDatasetColumnCount() {
        return datasetColumnCount;
    }

    public void setDatasetColumnCount(Integer datasetColumnCount) {
        this.datasetColumnCount = datasetColumnCount;
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

    public Long getSheetRowCount() {
        return sheetRowCount;
    }

    public void setSheetRowCount(Long sheetRowCount) {
        this.sheetRowCount = sheetRowCount;
    }

    public Integer getSheetColumnCount() {
        return sheetColumnCount;
    }

    public void setSheetColumnCount(Integer sheetColumnCount) {
        this.sheetColumnCount = sheetColumnCount;
    }
}
