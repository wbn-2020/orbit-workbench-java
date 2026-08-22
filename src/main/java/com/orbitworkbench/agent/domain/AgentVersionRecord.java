package com.orbitworkbench.agent.domain;

import java.time.Instant;

public class AgentVersionRecord {

    private Long id;
    private Long agentDefinitionId;
    private Integer versionNumber;
    private String status;
    private Long connectionId;
    private Long modelProfileId;
    private Long promptVersionId;
    private String configurationJson;
    private Instant publishedAt;
    private Instant createdAt;
    private Instant updatedAt;
    private String promptContent;
    private String promptVariablesJson;
    private Integer promptVersionNumber;
    private String connectionName;
    private String modelName;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAgentDefinitionId() {
        return agentDefinitionId;
    }

    public void setAgentDefinitionId(Long agentDefinitionId) {
        this.agentDefinitionId = agentDefinitionId;
    }

    public Integer getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(Integer versionNumber) {
        this.versionNumber = versionNumber;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getConnectionId() {
        return connectionId;
    }

    public void setConnectionId(Long connectionId) {
        this.connectionId = connectionId;
    }

    public Long getModelProfileId() {
        return modelProfileId;
    }

    public void setModelProfileId(Long modelProfileId) {
        this.modelProfileId = modelProfileId;
    }

    public Long getPromptVersionId() {
        return promptVersionId;
    }

    public void setPromptVersionId(Long promptVersionId) {
        this.promptVersionId = promptVersionId;
    }

    public String getConfigurationJson() {
        return configurationJson;
    }

    public void setConfigurationJson(String configurationJson) {
        this.configurationJson = configurationJson;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
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

    public String getPromptContent() {
        return promptContent;
    }

    public void setPromptContent(String promptContent) {
        this.promptContent = promptContent;
    }

    public String getPromptVariablesJson() {
        return promptVariablesJson;
    }

    public void setPromptVariablesJson(String promptVariablesJson) {
        this.promptVariablesJson = promptVariablesJson;
    }

    public Integer getPromptVersionNumber() {
        return promptVersionNumber;
    }

    public void setPromptVersionNumber(Integer promptVersionNumber) {
        this.promptVersionNumber = promptVersionNumber;
    }

    public String getConnectionName() {
        return connectionName;
    }

    public void setConnectionName(String connectionName) {
        this.connectionName = connectionName;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }
}
