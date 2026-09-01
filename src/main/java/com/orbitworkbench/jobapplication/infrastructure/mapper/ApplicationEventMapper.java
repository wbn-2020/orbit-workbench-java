package com.orbitworkbench.jobapplication.infrastructure.mapper;

import com.orbitworkbench.jobapplication.domain.ApplicationEventRecord;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ApplicationEventMapper {

    void insert(ApplicationEventRecord record);

    List<ApplicationEventRecord> listByApplication(@Param("applicationId") Long applicationId);

    /** 事件表对父行是 RESTRICT 外键：删投递前必须先删轨迹，否则整条删除路径会以数据库错误收场。 */
    int deleteByApplication(@Param("applicationId") Long applicationId);
}
