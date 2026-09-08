package com.orbitworkbench.focus.domain;

import java.time.LocalDate;

/** 专注统计读侧投影：某一天（仅 FOCUS 时段）的累计分钟数与次数。 */
public class FocusDayStat {

    private LocalDate date;
    private long focusMinutes;
    private long sessions;

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public long getFocusMinutes() { return focusMinutes; }
    public void setFocusMinutes(long focusMinutes) { this.focusMinutes = focusMinutes; }
    public long getSessions() { return sessions; }
    public void setSessions(long sessions) { this.sessions = sessions; }
}
