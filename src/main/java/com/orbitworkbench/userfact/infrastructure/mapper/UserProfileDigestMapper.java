package com.orbitworkbench.userfact.infrastructure.mapper;

import com.orbitworkbench.userfact.domain.UserProfileDigestRecord;
import org.apache.ibatis.annotations.Param;

/** 画像编译快照持久层：每用户至多一行，重编译即覆盖。 */
public interface UserProfileDigestMapper {

    UserProfileDigestRecord findByUser(@Param("userId") Long userId);

    /** 存在则整行覆盖（依赖 uk_user_profile_digest_user），不存在则插入。 */
    void upsert(UserProfileDigestRecord record);

    void deleteByUser(@Param("userId") Long userId);
}
