package com.orbitworkbench.userfact.domain;

import java.time.Instant;

/** 画像编译快照（V44）：把确认事实集编译成一份高密度画像，每用户至多一行。 */
public class UserProfileDigestRecord {

    private Long id;
    private Long userId;
    /** 编译产物正文（≤2000 字符）。 */
    private String digest;
    /** 编译时刻 CONFIRMED 事实 id 的 JSON 数组，用于快照时效判定。 */
    private String sourceFactIds;
    private int sourceCount;
    private String model;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getDigest() { return digest; }
    public void setDigest(String digest) { this.digest = digest; }
    public String getSourceFactIds() { return sourceFactIds; }
    public void setSourceFactIds(String sourceFactIds) { this.sourceFactIds = sourceFactIds; }
    public int getSourceCount() { return sourceCount; }
    public void setSourceCount(int sourceCount) { this.sourceCount = sourceCount; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
