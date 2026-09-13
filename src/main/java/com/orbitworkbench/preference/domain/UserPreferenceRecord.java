package com.orbitworkbench.preference.domain;

import java.time.Instant;

public class UserPreferenceRecord {

    private Long userId;
    private boolean notifyReportReady;
    private boolean notifyStudyDue;
    private boolean notifyInterview;
    private boolean notifyImportFailure;
    private boolean notifyAiFailure;
    private String timezoneId;
    /** 审计/用量明细保留天数（V43）；null = 永久保留（默认，不悄悄删用户数据）。 */
    private Integer auditRetentionDays;
    /** 通知保留天数（V43）；null = 永久保留。 */
    private Integer notificationRetentionDays;
    private int version;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public boolean isNotifyReportReady() {
        return notifyReportReady;
    }

    public void setNotifyReportReady(boolean notifyReportReady) {
        this.notifyReportReady = notifyReportReady;
    }

    public boolean isNotifyStudyDue() {
        return notifyStudyDue;
    }

    public void setNotifyStudyDue(boolean notifyStudyDue) {
        this.notifyStudyDue = notifyStudyDue;
    }

    public boolean isNotifyInterview() {
        return notifyInterview;
    }

    public void setNotifyInterview(boolean notifyInterview) {
        this.notifyInterview = notifyInterview;
    }

    public boolean isNotifyImportFailure() {
        return notifyImportFailure;
    }

    public void setNotifyImportFailure(boolean notifyImportFailure) {
        this.notifyImportFailure = notifyImportFailure;
    }

    public boolean isNotifyAiFailure() {
        return notifyAiFailure;
    }

    public void setNotifyAiFailure(boolean notifyAiFailure) {
        this.notifyAiFailure = notifyAiFailure;
    }

    public String getTimezoneId() {
        return timezoneId;
    }

    public Integer getAuditRetentionDays() {
        return auditRetentionDays;
    }

    public void setAuditRetentionDays(Integer auditRetentionDays) {
        this.auditRetentionDays = auditRetentionDays;
    }

    public Integer getNotificationRetentionDays() {
        return notificationRetentionDays;
    }

    public void setNotificationRetentionDays(Integer notificationRetentionDays) {
        this.notificationRetentionDays = notificationRetentionDays;
    }

    public void setTimezoneId(String timezoneId) {
        this.timezoneId = timezoneId;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
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
