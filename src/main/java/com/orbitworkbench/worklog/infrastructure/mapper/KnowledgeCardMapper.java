package com.orbitworkbench.worklog.infrastructure.mapper;

import com.orbitworkbench.worklog.domain.KnowledgeCardRecord;
import com.orbitworkbench.worklog.domain.KnowledgeCardRow;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface KnowledgeCardMapper {

    void insert(KnowledgeCardRecord record);

    KnowledgeCardRow findBySourceLog(@Param("userId") Long userId,
                                     @Param("sourceLogId") Long sourceLogId);

    KnowledgeCardRow findOwned(@Param("userId") Long userId, @Param("id") Long id);

    List<KnowledgeCardRow> listByUser(@Param("userId") Long userId,
                                      @Param("limit") int limit,
                                      @Param("offset") int offset);

    /** 该用户知识卡片总数（知识总览用）。 */
    long countByUser(@Param("userId") Long userId);

    /** 当日到期（next_review_date <= date）的卡片，按到期日升序。 */
    List<KnowledgeCardRow> listDue(@Param("userId") Long userId,
                                   @Param("date") java.time.LocalDate date,
                                   @Param("limit") int limit);

    /** 记一次回顾：阶梯由服务层算好写入；只推进，不回退。 */
    int markReviewed(@Param("id") Long id,
                     @Param("userId") Long userId,
                     @Param("reviewStage") int reviewStage,
                     @Param("nextReviewDate") java.time.LocalDate nextReviewDate,
                     @Param("lastReviewedAt") java.time.Instant lastReviewedAt);

    int updateContent(@Param("id") Long id,
                      @Param("userId") Long userId,
                      @Param("title") String title,
                      @Param("summary") String summary,
                      @Param("tagsJson") String tagsJson,
                      @Param("updatedAt") Instant updatedAt);
}
