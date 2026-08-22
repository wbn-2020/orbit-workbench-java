package com.orbitworkbench.search.infrastructure.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface SearchMapper {

    List<SearchResultRow> searchTasks(@Param("workspaceId") Long workspaceId,
                                      @Param("query") String query,
                                      @Param("scanLimit") int scanLimit,
                                      @Param("limit") int limit);

    List<SearchResultRow> searchDatasets(@Param("workspaceId") Long workspaceId,
                                         @Param("query") String query,
                                         @Param("scanLimit") int scanLimit,
                                         @Param("limit") int limit);

    List<SearchResultRow> searchDocuments(@Param("workspaceId") Long workspaceId,
                                          @Param("query") String query,
                                          @Param("scanLimit") int scanLimit,
                                          @Param("limit") int limit);

    List<SearchResultRow> searchArtifacts(@Param("workspaceId") Long workspaceId,
                                          @Param("query") String query,
                                          @Param("scanLimit") int scanLimit,
                                          @Param("limit") int limit);

    List<SearchResultRow> searchRuns(@Param("workspaceId") Long workspaceId,
                                     @Param("query") String query,
                                     @Param("scanLimit") int scanLimit,
                                     @Param("limit") int limit);
}
