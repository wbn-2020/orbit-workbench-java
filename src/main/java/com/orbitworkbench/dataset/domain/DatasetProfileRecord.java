package com.orbitworkbench.dataset.domain;

import java.time.Instant;

public class DatasetProfileRecord {

    private Long id;
    private Long datasetId;
    private Long sheetId;
    private Integer profileVersion;
    private Long rowCount;
    private Integer columnCount;
    private String summaryJson;
    private String qualityJson;
    private String profileStorageRef;
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Integer getProfileVersion() {
        return profileVersion;
    }

    public void setProfileVersion(Integer profileVersion) {
        this.profileVersion = profileVersion;
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

    public String getSummaryJson() {
        return summaryJson;
    }

    public void setSummaryJson(String summaryJson) {
        this.summaryJson = summaryJson;
    }

    public String getQualityJson() {
        return qualityJson;
    }

    public void setQualityJson(String qualityJson) {
        this.qualityJson = qualityJson;
    }

    public String getProfileStorageRef() {
        return profileStorageRef;
    }

    public void setProfileStorageRef(String profileStorageRef) {
        this.profileStorageRef = profileStorageRef;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
