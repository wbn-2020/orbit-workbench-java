package com.orbitworkbench.practice.infrastructure.mapper;

import com.orbitworkbench.practice.domain.PracticeAttemptRecord;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 尝试只新增、不更新不删除：`11` §5.3「新结果不得覆盖原记录」。 */
public interface PracticeAttemptMapper {

    void insert(PracticeAttemptRecord record);

    List<PracticeAttemptRecord> listByItem(@Param("itemId") Long itemId);

    long countByItem(@Param("itemId") Long itemId);

    Instant lastAttemptAtByUser(@Param("userId") Long userId, @Param("archived") Boolean archived);
}
