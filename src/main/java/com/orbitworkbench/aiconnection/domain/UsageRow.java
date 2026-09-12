package com.orbitworkbench.aiconnection.domain;

import java.math.BigDecimal;

/**
 * 用量账本的一行分组聚合（按日 / 场景 / 模型）。
 *
 * <p>{@code unpricedCalls} 是刻意的：一部分调用没有单价、算不出金额时，不能把
 * {@code costAmount} 当成「全部花费」展示——界面必须知道「这个数字覆盖了多少次调用」。
 */
public class UsageRow {

    private String key;
    private long calls;
    private long succeeded;
    private long failed;
    private long inputTokens;
    private long outputTokens;
    private BigDecimal costAmount;
    private long unpricedCalls;

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public long getCalls() { return calls; }
    public void setCalls(long calls) { this.calls = calls; }
    public long getSucceeded() { return succeeded; }
    public void setSucceeded(long succeeded) { this.succeeded = succeeded; }
    public long getFailed() { return failed; }
    public void setFailed(long failed) { this.failed = failed; }
    public long getInputTokens() { return inputTokens; }
    public void setInputTokens(long inputTokens) { this.inputTokens = inputTokens; }
    public long getOutputTokens() { return outputTokens; }
    public void setOutputTokens(long outputTokens) { this.outputTokens = outputTokens; }
    public BigDecimal getCostAmount() { return costAmount; }
    public void setCostAmount(BigDecimal costAmount) { this.costAmount = costAmount; }
    public long getUnpricedCalls() { return unpricedCalls; }
    public void setUnpricedCalls(long unpricedCalls) { this.unpricedCalls = unpricedCalls; }
}
