package com.orbitworkbench.craft.infrastructure.mapper;

import com.orbitworkbench.craft.domain.CraftNoteRecord;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface CraftNoteMapper {

    void insert(CraftNoteRecord record);

    List<CraftNoteRecord> listByUser(@Param("userId") Long userId);

    CraftNoteRecord findByIdAndUser(@Param("id") Long id, @Param("userId") Long userId);

    /** AI 蒸馏去重参考：已确认套路（标题 + 正文摘要）。 */
    List<CraftNoteRecord> listConfirmedForPrompt(@Param("userId") Long userId);

    /** 候选池是否有未处理建议（重复蒸馏防御）。 */
    long countAnalyzed(@Param("userId") Long userId);

    int updateContent(@Param("id") Long id,
                      @Param("userId") Long userId,
                      @Param("category") String category,
                      @Param("title") String title,
                      @Param("whenToUse") String whenToUse,
                      @Param("content") String content,
                      @Param("tagsJson") String tagsJson,
                      @Param("updatedAt") Instant updatedAt);

    /** 确认 AI 建议（可顺带修正措辞）。 */
    int confirm(@Param("id") Long id,
                @Param("userId") Long userId,
                @Param("category") String category,
                @Param("title") String title,
                @Param("whenToUse") String whenToUse,
                @Param("content") String content,
                @Param("tagsJson") String tagsJson,
                @Param("updatedAt") Instant updatedAt);

    int archive(@Param("id") Long id,
                @Param("userId") Long userId,
                @Param("updatedAt") Instant updatedAt);

    /** 置顶/取消置顶。 */
    int setPinned(@Param("id") Long id,
                  @Param("userId") Long userId,
                  @Param("pinned") boolean pinned,
                  @Param("updatedAt") Instant updatedAt);
}
