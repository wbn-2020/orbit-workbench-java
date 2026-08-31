package com.orbitworkbench.interview.domain;

import java.io.Serializable;

/** 某个评分规则版本下已就绪报告的数量，用于趋势选版与「样本不足」判定（14 §6）。 */
public class RuleVersionSample implements Serializable {

    private static final long serialVersionUID = 1L;

    private String ruleVersion;
    private long sampleCount;

    public String getRuleVersion() { return ruleVersion; }
    public void setRuleVersion(String ruleVersion) { this.ruleVersion = ruleVersion; }
    public long getSampleCount() { return sampleCount; }
    public void setSampleCount(long sampleCount) { this.sampleCount = sampleCount; }
}
