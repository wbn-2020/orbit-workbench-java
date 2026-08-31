package com.orbitworkbench.resume.domain;

import java.time.Instant;

/** 版本列表用的导出聚合，不落独立表，由 GROUP BY 派生。 */
public class ResumeExportSummary {

    private Long resumeVersionId;
    private long succeededCount;
    private Instant lastSucceededAt;

    public Long getResumeVersionId() { return resumeVersionId; }
    public void setResumeVersionId(Long resumeVersionId) { this.resumeVersionId = resumeVersionId; }
    public long getSucceededCount() { return succeededCount; }
    public void setSucceededCount(long succeededCount) { this.succeededCount = succeededCount; }
    public Instant getLastSucceededAt() { return lastSucceededAt; }
    public void setLastSucceededAt(Instant lastSucceededAt) { this.lastSucceededAt = lastSucceededAt; }
}
