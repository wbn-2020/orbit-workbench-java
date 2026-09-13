package com.orbitworkbench.preference.infrastructure.mapper;

import com.orbitworkbench.preference.domain.UserPreferenceRecord;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface UserPreferenceMapper {

    int insertIfAbsent(
            @Param("userId") Long userId,
            @Param("now") Instant now);

    UserPreferenceRecord findByUserId(@Param("userId") Long userId);

    /** 保留策略扫描：至少配了一项保留期的偏好行（全 null 的用户无需清理）。 */
    List<UserPreferenceRecord> listRetentionPolicies();

    int update(
            @Param("record") UserPreferenceRecord record,
            @Param("expectedVersion") int expectedVersion);
}
