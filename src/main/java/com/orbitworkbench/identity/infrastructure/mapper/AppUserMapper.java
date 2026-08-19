package com.orbitworkbench.identity.infrastructure.mapper;

import com.orbitworkbench.identity.domain.AppUserRecord;
import org.apache.ibatis.annotations.Param;

public interface AppUserMapper {
    int countAll();
    AppUserRecord findFirstForUpdate();
    int insert(AppUserRecord user);
    AppUserRecord findByUsername(@Param("username") String username);
    AppUserRecord findById(@Param("id") Long id);
    int updateLastLogin(@Param("id") Long id);
    int updatePassword(@Param("id") Long id,
                       @Param("passwordHash") String passwordHash,
                       @Param("expectedPasswordHash") String expectedPasswordHash);
}
