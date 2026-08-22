package com.orbitworkbench.tool.domain;

import java.time.Instant;

public class ToolCallRecord {

    private Long id;
    private Long agentRunId;
    private Long workflowRunId;
    private Long stepId;
    private Long workflowNodeRunId;
    private Long modelCallId;
    private Long toolDefinitionId;
    private Long toolVersionId;
    private String toolCode;
    private Integer toolVersion;
    private String callKey;
    private String argumentsHash;
    private String status;
    private String argumentsSnapshotRef;
    private String argumentsSummary;
    private String resultSnapshotRef;
    private String resultSummary;
    private Integer resultSizeBytes;
    private Long retryOfToolCallId;
    private Instant startedAt;
    private Instant finishedAt;
    private String errorCode;
    private String errorSummary;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAgentRunId() {
        return agentRunId;
    }

    public void setAgentRunId(Long agentRunId) {
        this.agentRunId = agentRunId;
    }

    public Long getWorkflowRunId() {
        return workflowRunId;
    }

    public void setWorkflowRunId(Long workflowRunId) {
        this.workflowRunId = workflowRunId;
    }

    public Long getStepId() {
        return stepId;
    }

    public void setStepId(Long stepId) {
        this.stepId = stepId;
    }

    public Long getWorkflowNodeRunId() {
        return workflowNodeRunId;
    }

    public void setWorkflowNodeRunId(Long workflowNodeRunId) {
        this.workflowNodeRunId = workflowNodeRunId;
    }

    public Long getModelCallId() {
        return modelCallId;
    }

    public void setModelCallId(Long modelCallId) {
        this.modelCallId = modelCallId;
    }

    public Long getToolDefinitionId() {
        return toolDefinitionId;
    }

    public void setToolDefinitionId(Long toolDefinitionId) {
        this.toolDefinitionId = toolDefinitionId;
    }

    public Long getToolVersionId() {
        return toolVersionId;
    }

    public void setToolVersionId(Long toolVersionId) {
        this.toolVersionId = toolVersionId;
    }

    public String getToolCode() {
        return toolCode;
    }

    public void setToolCode(String toolCode) {
        this.toolCode = toolCode;
    }

    public Integer getToolVersion() {
        return toolVersion;
    }

    public void setToolVersion(Integer toolVersion) {
        this.toolVersion = toolVersion;
    }

    public String getCallKey() {
        return callKey;
    }

    public void setCallKey(String callKey) {
        this.callKey = callKey;
    }

    public String getArgumentsHash() {
        return argumentsHash;
    }

    public void setArgumentsHash(String argumentsHash) {
        this.argumentsHash = argumentsHash;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getArgumentsSnapshotRef() {
        return argumentsSnapshotRef;
    }

    public void setArgumentsSnapshotRef(String argumentsSnapshotRef) {
        this.argumentsSnapshotRef = argumentsSnapshotRef;
    }

    public String getArgumentsSummary() {
        return argumentsSummary;
    }

    public void setArgumentsSummary(String argumentsSummary) {
        this.argumentsSummary = argumentsSummary;
    }

    public String getResultSnapshotRef() {
        return resultSnapshotRef;
    }

    public void setResultSnapshotRef(String resultSnapshotRef) {
        this.resultSnapshotRef = resultSnapshotRef;
    }

    public String getResultSummary() {
        return resultSummary;
    }

    public void setResultSummary(String resultSummary) {
        this.resultSummary = resultSummary;
    }

    public Integer getResultSizeBytes() {
        return resultSizeBytes;
    }

    public void setResultSizeBytes(Integer resultSizeBytes) {
        this.resultSizeBytes = resultSizeBytes;
    }

    public Long getRetryOfToolCallId() {
        return retryOfToolCallId;
    }

    public void setRetryOfToolCallId(Long retryOfToolCallId) {
        this.retryOfToolCallId = retryOfToolCallId;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorSummary() {
        return errorSummary;
    }

    public void setErrorSummary(String errorSummary) {
        this.errorSummary = errorSummary;
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
