package com.orbitworkbench.jobprofile.infrastructure.mapper;

import com.orbitworkbench.jobprofile.domain.JobProfileRecord;
import org.apache.ibatis.annotations.Param;

public interface JobProfileMapper {
    JobProfileRecord findByUserId(@Param("userId") Long userId);
    void insert(JobProfileRecord record);
    int update(JobProfileRecord record);
}
