package com.orbitworkbench.studyplan.infrastructure.mapper;

import com.orbitworkbench.studyplan.domain.StudyTaskRecord;
import com.orbitworkbench.studyplan.domain.StudyTaskSource;
import com.orbitworkbench.studyplan.domain.StudyTaskStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface StudyTaskMapper {

    void insert(StudyTaskRecord record);

    StudyTaskRecord findById(@Param("id") Long id);

    List<StudyTaskRecord> listByUser(
            @Param("userId") Long userId,
            @Param("status") StudyTaskStatus status);

    List<StudyTaskRecord> listDueActive(@Param("throughDate") LocalDate throughDate);

    List<StudyTaskRecord> listActiveByDueRange(
            @Param("userId") Long userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    int updateStatus(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("expectedStatus") StudyTaskStatus expectedStatus,
            @Param("status") StudyTaskStatus status,
            @Param("dueDate") LocalDate dueDate,
            @Param("updatedAt") Instant updatedAt);

    int delete(
            @Param("id") Long id,
            @Param("userId") Long userId);

    /** V49：已有练习任务的套路 id 集合（用于本事库回显「已在练习计划」）。 */
    List<Long> listCraftIdsWithPracticeTask(@Param("userId") Long userId);

    int countBySourceTitle(
            @Param("userId") Long userId,
            @Param("sourceType") StudyTaskSource sourceType,
            @Param("sourceId") Long sourceId,
            @Param("title") String title);
}
