package com.orbitworkbench.jobmatch.infrastructure.mapper;

import com.orbitworkbench.jobmatch.domain.JobMatchResultRecord;
import com.orbitworkbench.jobmatch.domain.JobMatchRow;
import com.orbitworkbench.jobmatch.domain.MatchConfirmation;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface JobMatchResultMapper {

    void insert(JobMatchResultRecord record);

    JobMatchRow findOwned(@Param("userId") Long userId, @Param("matchId") Long matchId);

    List<JobMatchRow> listByPosting(@Param("userId") Long userId,
                                    @Param("postingId") Long postingId,
                                    @Param("limit") int limit,
                                    @Param("offset") int offset);

    long countByPosting(@Param("userId") Long userId, @Param("postingId") Long postingId);

    /**
     * 确认/驳回是对既有行的状态更新，不新增行（`17` §6）。
     * {@code expectedStatus} 非空时构成一次乐观比较，避免两个标签页把已确认的结论改回待定。
     */
    int updateConfirmation(@Param("matchId") Long matchId,
                           @Param("userId") Long userId,
                           @Param("status") MatchConfirmation status,
                           @Param("expectedStatus") MatchConfirmation expectedStatus,
                           @Param("confirmedAt") Instant confirmedAt);
}
