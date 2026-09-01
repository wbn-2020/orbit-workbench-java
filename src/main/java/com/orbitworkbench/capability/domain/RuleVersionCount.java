package com.orbitworkbench.capability.domain;

import java.io.Serializable;

/**
 * 某个评分规则版本下已就绪报告的条数（`16` §7）。
 * {@code ruleVersion} 为 {@code null} 的那一组就是「未记录规则版本」的历史报告，
 * 它们不参与趋势，但必须在来源区单独计数，不得静默丢弃。
 */
public class RuleVersionCount implements Serializable {

    private static final long serialVersionUID = 1L;

    private String ruleVersion;
    private long sampleCount;

    public String getRuleVersion() { return ruleVersion; }
    public void setRuleVersion(String ruleVersion) { this.ruleVersion = ruleVersion; }
    public long getSampleCount() { return sampleCount; }
    public void setSampleCount(long sampleCount) { this.sampleCount = sampleCount; }
}
