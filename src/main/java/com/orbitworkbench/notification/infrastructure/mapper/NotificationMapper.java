package com.orbitworkbench.notification.infrastructure.mapper;

import com.orbitworkbench.notification.domain.NotificationRecord;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface NotificationMapper {

    int insertIgnore(NotificationRecord record);

    NotificationRecord findByIdAndUser(
            @Param("id") Long id,
            @Param("userId") Long userId);

    List<NotificationRecord> listByUser(
            @Param("userId") Long userId,
            @Param("unreadOnly") boolean unreadOnly,
            @Param("limit") int limit,
            @Param("offset") int offset);

    int countByUser(
            @Param("userId") Long userId,
            @Param("unreadOnly") boolean unreadOnly);

    int markRead(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("readAt") Instant readAt);

    int markAllRead(
            @Param("userId") Long userId,
            @Param("readAt") Instant readAt);
}
