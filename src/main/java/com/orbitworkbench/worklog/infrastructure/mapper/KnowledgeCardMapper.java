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

    int updateContent(@Param("id") Long id,
                      @Param("userId") Long userId,
                      @Param("title") String title,
                      @Param("summary") String summary,
                      @Param("tagsJson") String tagsJson,
                      @Param("updatedAt") Instant updatedAt);
}
