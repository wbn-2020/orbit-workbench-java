package com.orbitworkbench.mcp.domain;

import java.time.Instant;

public class McpToolRecord {

    private Long id;
    private Long mcpServerId;
    private String toolCode;
    private String toolName;
    private String title;
    private String description;
    private Long toolCatalogId;
    private Long toolVersionId;
    private Integer versionNumber;
    private boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getMcpServerId() {
        return mcpServerId;
    }

    public void setMcpServerId(Long mcpServerId) {
        this.mcpServerId = mcpServerId;
    }

    public String getToolCode() {
        return toolCode;
    }

    public void setToolCode(String toolCode) {
        this.toolCode = toolCode;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getToolCatalogId() {
        return toolCatalogId;
    }

    public void setToolCatalogId(Long toolCatalogId) {
        this.toolCatalogId = toolCatalogId;
    }

    public Long getToolVersionId() {
        return toolVersionId;
    }

    public void setToolVersionId(Long toolVersionId) {
        this.toolVersionId = toolVersionId;
    }

    public Integer getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(Integer versionNumber) {
        this.versionNumber = versionNumber;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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
