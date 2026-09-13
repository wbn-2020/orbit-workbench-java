package com.orbitworkbench.userfact.infrastructure.mapper;

import com.orbitworkbench.userfact.domain.UserFactRecord;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface UserFactMapper {

    void insert(UserFactRecord record);

    List<UserFactRecord> listByUser(@Param("userId") Long userId);

    UserFactRecord findByIdAndUser(@Param("id") Long id, @Param("userId") Long userId);

    /** 注入链路读取：CONFIRMED 事实，按类型稳定排序、id 截断。 */
    List<UserFactRecord> listConfirmed(@Param("userId") Long userId, @Param("limit") int limit);

    /** 确认（可同时修正类型/标题/内容）：ANALYZED|手动草稿 -> CONFIRMED。 */
    int confirm(@Param("id") Long id,
                @Param("userId") Long userId,
                @Param("factType") String factType,
                @Param("title") String title,
                @Param("content") String content,
                @Param("confirmedAt") Instant confirmedAt,
                @Param("updatedAt") Instant updatedAt);

    /** 归档：MANUAL 为用户主动；SUPERSEDED 为确认新建议时取代同类型旧事实。 */
    int archive(@Param("id") Long id,
                @Param("userId") Long userId,
                @Param("reason") String reason,
                @Param("updatedAt") Instant updatedAt);

    /** 复查（V43）：用户确认这条已确认事实「仍然成立」，把 last_seen_at 推到当下。 */
    int reaffirm(@Param("id") Long id,
                 @Param("userId") Long userId,
                 @Param("seenAt") Instant seenAt,
                 @Param("updatedAt") Instant updatedAt);

    /** 重复沉淀防御：当前 ANALYZED 候选数量。 */
    long countAnalyzed(@Param("userId") Long userId);

    /** 生成建议时携带的已确认事实清单（让模型知道什么已记录、什么被取代）。 */
    List<UserFactRecord> listConfirmedForPrompt(@Param("userId") Long userId);
}
