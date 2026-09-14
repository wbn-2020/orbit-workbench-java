package com.orbitworkbench.userfact.infrastructure.mapper;

import com.orbitworkbench.userfact.domain.UserFocusNoteRecord;
import org.apache.ibatis.annotations.Param;

/** 近期关注（每用户至多一行）。 */
public interface UserFocusNoteMapper {

    UserFocusNoteRecord findByUser(@Param("userId") Long userId);

    /** 存在则整行覆盖（依赖 uk_user_focus_note_user），不存在则插入。 */
    void upsert(UserFocusNoteRecord record);

    void deleteByUser(@Param("userId") Long userId);
}
