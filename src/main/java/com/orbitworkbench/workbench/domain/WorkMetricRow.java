package com.orbitworkbench.workbench.domain;

/** 复盘用工作记录行：类别与蒸馏状态。 */
public class WorkMetricRow extends WindowRow {

    private String category;
    private boolean distilled;

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public boolean isDistilled() { return distilled; }
    public void setDistilled(boolean distilled) { this.distilled = distilled; }
}
