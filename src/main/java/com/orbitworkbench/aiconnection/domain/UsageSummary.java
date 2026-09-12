package com.orbitworkbench.aiconnection.domain;

import java.math.BigDecimal;

/**
 * 时间窗内的用量总量。
 *
 * <p>{@code pricedCalls} 与 {@code unpricedCalls} 一起返回：前者是有单价、成本已计入
 * {@code costAmount} 的调用数。若只给金额，用户会以为那就是全部花费。
 */
public class UsageSummary {

    private long calls;
    private long succeeded;
    private long failed;
    private long inputTokens;
    private long outputTokens;
    private BigDecimal costAmount;
    private long pricedCalls;
    private long unpricedCalls;

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
    public long getPricedCalls() { return pricedCalls; }
    public void setPricedCalls(long pricedCalls) { this.pricedCalls = pricedCalls; }
    public long getUnpricedCalls() { return unpricedCalls; }
    public void setUnpricedCalls(long unpricedCalls) { this.unpricedCalls = unpricedCalls; }
}
