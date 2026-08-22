package com.orbitworkbench.agent.domain;

import java.time.Instant;

public class AgentDefinitionDetailRecord {

    private Long id;
    private Long workspaceId;
    private String code;
    private String name;
    private String description;
    private String status;
    private Long defaultConnectionId;
    private Long defaultModelProfileId;
    private Long promptVersionId;
    private Long publishedVersionId;
    private Long version;
    private Long promptTemplateId;
    private String promptTemplateName;
    private Integer promptVersionNumber;
    private String promptContent;
    private String promptVariablesJson;
    private String configurationJson;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(Long workspaceId) {
        this.workspaceId = workspaceId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getDefaultConnectionId() {
        return defaultConnectionId;
    }

    public void setDefaultConnectionId(Long defaultConnectionId) {
        this.defaultConnectionId = defaultConnectionId;
    }

    public Long getDefaultModelProfileId() {
        return defaultModelProfileId;
    }

    public void setDefaultModelProfileId(Long defaultModelProfileId) {
        this.defaultModelProfileId = defaultModelProfileId;
    }

    public Long getPromptVersionId() {
        return promptVersionId;
    }

    public void setPromptVersionId(Long promptVersionId) {
        this.promptVersionId = promptVersionId;
    }

    public Long getPublishedVersionId() {
        return publishedVersionId;
    }

    public void setPublishedVersionId(Long publishedVersionId) {
        this.publishedVersionId = publishedVersionId;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Long getPromptTemplateId() {
        return promptTemplateId;
    }

    public void setPromptTemplateId(Long promptTemplateId) {
        this.promptTemplateId = promptTemplateId;
    }

    public String getPromptTemplateName() {
        return promptTemplateName;
    }

    public void setPromptTemplateName(String promptTemplateName) {
        this.promptTemplateName = promptTemplateName;
    }

    public Integer getPromptVersionNumber() {
        return promptVersionNumber;
    }

    public void setPromptVersionNumber(Integer promptVersionNumber) {
        this.promptVersionNumber = promptVersionNumber;
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

    public String getConfigurationJson() {
        return configurationJson;
    }

    public void setConfigurationJson(String configurationJson) {
        this.configurationJson = configurationJson;
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
