package com.orbitworkbench.studyplan.domain;

/**
 * V58：一个学习目标的任务统计投影（GOAL 来源按 source_id 分组）。
 * 目标卡的「已拆 N 步 · 完成 M」与派生进度都读这一行——列表与派生同源，不另查一遍。
 */
public class GoalTaskStats {

    private Long goalId;
    private long total;
    private long completed;

    public Long getGoalId() { return goalId; }
    public void setGoalId(Long goalId) { this.goalId = goalId; }
    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
    public long getCompleted() { return completed; }
    public void setCompleted(long completed) { this.completed = completed; }
}
