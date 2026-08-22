package com.orbitworkbench.skill.domain;

import java.time.Instant;
import java.util.List;

public class SkillVersionRecord {

    private Long id;
    private Long skillDefinitionId;
    private Integer versionNumber;
    private String status;
    private Long promptVersionId;
    private Integer promptVersionNumber;
    private String inputSchemaJson;
    private String outputType;
    private String runtimeLimitsJson;
    private Instant publishedAt;
    private Instant createdAt;
    private Instant updatedAt;
    private List<Long> toolVersionIds = List.of();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSkillDefinitionId() {
        return skillDefinitionId;
    }

    public void setSkillDefinitionId(Long skillDefinitionId) {
        this.skillDefinitionId = skillDefinitionId;
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

    public Long getPromptVersionId() {
        return promptVersionId;
    }

    public void setPromptVersionId(Long promptVersionId) {
        this.promptVersionId = promptVersionId;
    }

    public Integer getPromptVersionNumber() {
        return promptVersionNumber;
    }

    public void setPromptVersionNumber(Integer promptVersionNumber) {
        this.promptVersionNumber = promptVersionNumber;
    }

    public String getInputSchemaJson() {
        return inputSchemaJson;
    }

    public void setInputSchemaJson(String inputSchemaJson) {
        this.inputSchemaJson = inputSchemaJson;
    }

    public String getOutputType() {
        return outputType;
    }

    public void setOutputType(String outputType) {
        this.outputType = outputType;
    }

    public String getRuntimeLimitsJson() {
        return runtimeLimitsJson;
    }

    public void setRuntimeLimitsJson(String runtimeLimitsJson) {
        this.runtimeLimitsJson = runtimeLimitsJson;
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

    public List<Long> getToolVersionIds() {
        return toolVersionIds;
    }

    public void setToolVersionIds(List<Long> toolVersionIds) {
        this.toolVersionIds = toolVersionIds == null ? List.of() : List.copyOf(toolVersionIds);
    }
}
