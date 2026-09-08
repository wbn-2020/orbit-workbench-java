package com.orbitworkbench.focus.infrastructure.mapper;

import com.orbitworkbench.focus.domain.FocusDayStat;
import com.orbitworkbench.focus.domain.FocusSessionRecord;
import com.orbitworkbench.focus.domain.FocusSessionRow;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface FocusSessionMapper {

    void insert(FocusSessionRecord record);

    FocusSessionRow findOwned(@Param("userId") Long userId, @Param("id") Long id);

    FocusSessionRow findByIdempotencyKey(@Param("userId") Long userId,
                                         @Param("idempotencyKey") String idempotencyKey);

    List<FocusSessionRow> listByUser(@Param("userId") Long userId,
                                     @Param("limit") int limit,
                                     @Param("offset") int offset);

    /**
     * 读取统计窗口内的专注时段原始记录。日期归档在 Java 中按用户 ZoneId 完成，
     * 以正确处理夏令时切换并避免依赖 MySQL 时区表。
     */
    List<FocusSessionRow> listFocusSince(@Param("userId") Long userId,
                                         @Param("since") Instant since);
}
