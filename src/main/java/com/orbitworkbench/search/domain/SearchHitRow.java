package com.orbitworkbench.search.domain;

import java.time.Instant;

/**
 * 全局搜索命中的原始行。各数据域的检索 SQL 把命中字段映射到这里，
 * 由 {@code SearchService} 统一裁剪摘要、拼装跳转路由并分组。
 * 字段按域取用：不涉及的域对应字段为 null。
 */
public class SearchHitRow {

    private Long id;
    private Long projectId;
    private Long projectVersionId;
    private Long sessionId;
    private Integer turnNo;
    private String title;
    private String body;
    private String sub;
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public Long getProjectVersionId() { return projectVersionId; }
    public void setProjectVersionId(Long projectVersionId) { this.projectVersionId = projectVersionId; }

    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }

    public Integer getTurnNo() { return turnNo; }
    public void setTurnNo(Integer turnNo) { this.turnNo = turnNo; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public String getSub() { return sub; }
    public void setSub(String sub) { this.sub = sub; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
