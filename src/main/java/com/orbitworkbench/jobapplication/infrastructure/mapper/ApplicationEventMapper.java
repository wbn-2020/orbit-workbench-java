package com.orbitworkbench.jobapplication.infrastructure.mapper;

import com.orbitworkbench.jobapplication.domain.ApplicationEventRecord;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ApplicationEventMapper {

    void insert(ApplicationEventRecord record);

    List<ApplicationEventRecord> listByApplication(@Param("applicationId") Long applicationId);
}
