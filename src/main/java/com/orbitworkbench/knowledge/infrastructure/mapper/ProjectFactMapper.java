package com.orbitworkbench.knowledge.infrastructure.mapper;

import com.orbitworkbench.knowledge.domain.FactSource;
import com.orbitworkbench.knowledge.domain.FactStatus;
import com.orbitworkbench.knowledge.domain.ProjectFactRecord;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ProjectFactMapper {

    void insert(ProjectFactRecord record);

    int deleteAnalyzedByVersion(
            @Param("userId") Long userId,
            @Param("projectVersionId") Long projectVersionId);

    List<ProjectFactRecord> listByVersion(
            @Param("userId") Long userId,
            @Param("projectVersionId") Long projectVersionId);

    ProjectFactRecord findByIdAndUser(
            @Param("id") Long id,
            @Param("userId") Long userId);

    /** 跨全部项目的状态计数（知识总览用）。 */
    long countByStatus(@Param("userId") Long userId, @Param("status") String status);

    /** 指定状态的最近条目（知识总览用，按 id 倒序）。 */
    List<ProjectFactRecord> recentByStatus(@Param("userId") Long userId,
                                           @Param("status") String status,
                                           @Param("limit") int limit);

    int confirm(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("factType") String factType,
            @Param("title") String title,
            @Param("content") String content,
            @Param("confirmedAt") Instant confirmedAt,
            @Param("updatedAt") Instant updatedAt);

    int archive(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("updatedAt") Instant updatedAt);
}
