package com.orbitworkbench.workbench.domain;

/** 复盘用专注行：时长与模式。 */
public class FocusMetricRow extends WindowRow {

    private int durationMinutes;
    private String mode;

    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
}
