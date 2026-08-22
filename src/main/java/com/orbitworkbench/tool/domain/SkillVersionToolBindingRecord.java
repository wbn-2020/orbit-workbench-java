package com.orbitworkbench.tool.domain;

import java.time.Instant;

public class SkillVersionToolBindingRecord {

    private Long skillVersionId;
    private Long toolVersionId;
    private Integer bindingOrder;
    private Instant createdAt;

    public Long getSkillVersionId() {
        return skillVersionId;
    }

    public void setSkillVersionId(Long skillVersionId) {
        this.skillVersionId = skillVersionId;
    }

    public Long getToolVersionId() {
        return toolVersionId;
    }

    public void setToolVersionId(Long toolVersionId) {
        this.toolVersionId = toolVersionId;
    }

    public Integer getBindingOrder() {
        return bindingOrder;
    }

    public void setBindingOrder(Integer bindingOrder) {
        this.bindingOrder = bindingOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
