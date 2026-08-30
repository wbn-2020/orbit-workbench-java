package com.orbitworkbench.search.infrastructure.mapper;

import com.orbitworkbench.search.domain.SearchHitRow;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 全局搜索只读检索。所有查询都带 {@code user_id} 过滤（或经会话/项目连接间接过滤），
 * 跨表仅做只读 JOIN，不写任何数据。知识块优先 FULLTEXT，未命中再用 LIKE 兜底。
 */
public interface SearchMapper {

    List<SearchHitRow> searchProjects(@Param("userId") Long userId,
                                      @Param("keyword") String keyword,
                                      @Param("limit") int limit);

    List<SearchHitRow> searchProjectVersions(@Param("userId") Long userId,
                                             @Param("keyword") String keyword,
                                             @Param("limit") int limit);

    List<SearchHitRow> searchKnowledge(@Param("userId") Long userId,
                                       @Param("keyword") String keyword,
                                       @Param("limit") int limit);

    List<SearchHitRow> searchKnowledgeLike(@Param("userId") Long userId,
                                           @Param("keyword") String keyword,
                                           @Param("limit") int limit);

    List<SearchHitRow> searchInterviewSessions(@Param("userId") Long userId,
                                               @Param("keyword") String keyword,
                                               @Param("limit") int limit);

    List<SearchHitRow> searchInterviewTurns(@Param("userId") Long userId,
                                            @Param("keyword") String keyword,
                                            @Param("limit") int limit);

    List<SearchHitRow> searchReports(@Param("userId") Long userId,
                                     @Param("keyword") String keyword,
                                     @Param("limit") int limit);

    List<SearchHitRow> searchJobApplications(@Param("userId") Long userId,
                                             @Param("keyword") String keyword,
                                             @Param("limit") int limit);
}
