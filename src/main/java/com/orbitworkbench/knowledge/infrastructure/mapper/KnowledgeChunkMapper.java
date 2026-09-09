package com.orbitworkbench.knowledge.infrastructure.mapper;

import com.orbitworkbench.knowledge.domain.KnowledgeChunkRecord;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface KnowledgeChunkMapper {

    void insert(KnowledgeChunkRecord record);

    int deleteByVersion(
            @Param("userId") Long userId,
            @Param("projectVersionId") Long projectVersionId);

    int countByVersion(
            @Param("userId") Long userId,
            @Param("projectVersionId") Long projectVersionId);

    /** 该用户全部项目知识块总数（知识总览用）。 */
    long countByUser(@Param("userId") Long userId);

    /**
     * 全文检索：优先 FULLTEXT ngram 相关度排序；SQL 内按 versionId 可选过滤。
     */
    List<KnowledgeChunkRecord> search(
            @Param("userId") Long userId,
            @Param("projectVersionId") Long projectVersionId,
            @Param("keyword") String keyword,
            @Param("limit") int limit);

    List<KnowledgeChunkRecord> searchFallbackLike(
            @Param("userId") Long userId,
            @Param("projectVersionId") Long projectVersionId,
            @Param("keyword") String keyword,
            @Param("limit") int limit);
}
