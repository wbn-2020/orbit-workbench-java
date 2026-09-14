package com.orbitworkbench.learning.infrastructure.mapper;

import com.orbitworkbench.learning.domain.LearningGoalRecord;
import com.orbitworkbench.learning.domain.LearningGoalRow;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface LearningGoalMapper {

    void insert(LearningGoalRecord record);

    LearningGoalRow findOwned(@Param("userId") Long userId, @Param("id") Long id);

    LearningGoalRow findByIdempotencyKey(@Param("userId") Long userId,
                                         @Param("idempotencyKey") String idempotencyKey);

    List<LearningGoalRow> listByUser(@Param("userId") Long userId,
                                     @Param("limit") int limit,
                                     @Param("offset") int offset);

    int updateStatusAndProgress(@Param("id") Long id,
                                @Param("userId") Long userId,
                                @Param("status") String status,
                                @Param("progress") int progress,
                                @Param("updatedAt") Instant updatedAt);

    /** V52：已转成学习目标的事实 id 集合（事实卡片回显「已成目标」）。 */
    List<Long> listFactIdsWithGoals(@Param("userId") Long userId);

    /** V52：某条事实已转成的目标数（fact→goal 幂等判定）。 */
    int countBySourceFact(@Param("userId") Long userId, @Param("factId") Long factId);
}
