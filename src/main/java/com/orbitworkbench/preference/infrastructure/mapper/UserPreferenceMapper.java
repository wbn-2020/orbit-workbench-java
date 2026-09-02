package com.orbitworkbench.preference.infrastructure.mapper;

import com.orbitworkbench.preference.domain.UserPreferenceRecord;
import java.time.Instant;
import org.apache.ibatis.annotations.Param;

public interface UserPreferenceMapper {

    int insertIfAbsent(
            @Param("userId") Long userId,
            @Param("now") Instant now);

    UserPreferenceRecord findByUserId(@Param("userId") Long userId);

    int update(
            @Param("record") UserPreferenceRecord record,
            @Param("expectedVersion") int expectedVersion);
}
