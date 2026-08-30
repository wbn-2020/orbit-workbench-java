package com.orbitworkbench.schedule.infrastructure.mapper;

import com.orbitworkbench.schedule.domain.ScheduleEventRecord;
import com.orbitworkbench.schedule.domain.ScheduleStatus;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ScheduleEventMapper {

    int insert(ScheduleEventRecord record);

    ScheduleEventRecord findByIdAndUser(
            @Param("id") Long id,
            @Param("userId") Long userId);

    List<ScheduleEventRecord> listCustomBetween(
            @Param("userId") Long userId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    int update(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("title") String title,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt,
            @Param("allDay") boolean allDay,
            @Param("reminderMinutes") Integer reminderMinutes,
            @Param("resourceRoute") String resourceRoute,
            @Param("note") String note,
            @Param("updatedAt") Instant updatedAt);

    int updateStatus(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("status") ScheduleStatus status,
            @Param("updatedAt") Instant updatedAt);

    int delete(
            @Param("id") Long id,
            @Param("userId") Long userId);
}
