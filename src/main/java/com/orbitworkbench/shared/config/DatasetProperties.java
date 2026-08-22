package com.orbitworkbench.shared.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("orbit.dataset")
public class DatasetProperties {

    private long maxFileBytes = 20L * 1024 * 1024;
    private int maxRows = 100_000;
    private int maxColumns = 200;
    private int maxSheets = 20;
    private int maxPreviewRows = 100;
    private int maxCellCharacters = 32_768;
    private int maxToolResultBytes = 256 * 1024;
    private Duration parseTimeout = Duration.ofMinutes(2);

    public long getMaxFileBytes() {
        return maxFileBytes;
    }

    public void setMaxFileBytes(long maxFileBytes) {
        this.maxFileBytes = maxFileBytes;
    }

    public int getMaxRows() {
        return maxRows;
    }

    public void setMaxRows(int maxRows) {
        this.maxRows = maxRows;
    }

    public int getMaxColumns() {
        return maxColumns;
    }

    public void setMaxColumns(int maxColumns) {
        this.maxColumns = maxColumns;
    }

    public int getMaxSheets() {
        return maxSheets;
    }

    public void setMaxSheets(int maxSheets) {
        this.maxSheets = maxSheets;
    }

    public int getMaxPreviewRows() {
        return maxPreviewRows;
    }

    public void setMaxPreviewRows(int maxPreviewRows) {
        this.maxPreviewRows = maxPreviewRows;
    }

    public int getMaxCellCharacters() {
        return maxCellCharacters;
    }

    public void setMaxCellCharacters(int maxCellCharacters) {
        this.maxCellCharacters = maxCellCharacters;
    }

    public int getMaxToolResultBytes() {
        return maxToolResultBytes;
    }

    public void setMaxToolResultBytes(int maxToolResultBytes) {
        this.maxToolResultBytes = maxToolResultBytes;
    }

    public Duration getParseTimeout() {
        return parseTimeout;
    }

    public void setParseTimeout(Duration parseTimeout) {
        this.parseTimeout = parseTimeout;
    }
}
