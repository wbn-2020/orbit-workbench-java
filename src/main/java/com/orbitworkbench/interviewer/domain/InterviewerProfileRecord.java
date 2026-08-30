package com.orbitworkbench.interviewer.domain;

import java.time.Instant;

public class InterviewerProfileRecord {

    private Long id;
    private Long userId;
    private String code;
    private String name;
    private String description;
    private String systemPrompt;
    private String topicMode;
    private String focusTagsJson;
    private Integer defaultQuestionLimit;
    private Integer defaultFollowUpLimit;
    private Boolean builtIn;
    private Boolean archived;
    private Integer version;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
    public String getTopicMode() { return topicMode; }
    public void setTopicMode(String topicMode) { this.topicMode = topicMode; }
    public String getFocusTagsJson() { return focusTagsJson; }
    public void setFocusTagsJson(String focusTagsJson) { this.focusTagsJson = focusTagsJson; }
    public Integer getDefaultQuestionLimit() { return defaultQuestionLimit; }
    public void setDefaultQuestionLimit(Integer defaultQuestionLimit) {
        this.defaultQuestionLimit = defaultQuestionLimit;
    }
    public Integer getDefaultFollowUpLimit() { return defaultFollowUpLimit; }
    public void setDefaultFollowUpLimit(Integer defaultFollowUpLimit) {
        this.defaultFollowUpLimit = defaultFollowUpLimit;
    }
    public Boolean getBuiltIn() { return builtIn; }
    public void setBuiltIn(Boolean builtIn) { this.builtIn = builtIn; }
    public Boolean getArchived() { return archived; }
    public void setArchived(Boolean archived) { this.archived = archived; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
