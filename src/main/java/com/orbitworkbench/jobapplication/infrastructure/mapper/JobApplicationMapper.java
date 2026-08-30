package com.orbitworkbench.jobapplication.infrastructure.mapper;

import com.orbitworkbench.jobapplication.domain.ApplicationStage;
import com.orbitworkbench.jobapplication.domain.JobApplicationRecord;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface JobApplicationMapper {

    void insert(JobApplicationRecord record);

    JobApplicationRecord findById(@Param("id") Long id);

    List<JobApplicationRecord> listByUser(
            @Param("userId") Long userId,
            @Param("includeArchived") boolean includeArchived);

    List<JobApplicationRecord> listWithInterviewDateBetween(
            @Param("userId") Long userId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    int updateEditable(JobApplicationRecord record);

    int updateStage(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("expectedStage") ApplicationStage expectedStage,
            @Param("stage") ApplicationStage stage,
            @Param("stageChangedAt") Instant stageChangedAt,
            @Param("updatedAt") Instant updatedAt);

    int updateArchived(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("archived") boolean archived,
            @Param("updatedAt") Instant updatedAt);

    int delete(
            @Param("id") Long id,
            @Param("userId") Long userId);
}
