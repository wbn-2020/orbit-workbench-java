package com.orbitworkbench.resume.infrastructure.mapper;

import com.orbitworkbench.resume.domain.ResumeExportRecord;
import com.orbitworkbench.resume.domain.ResumeExportSummary;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ResumeExportMapper {

    void insert(ResumeExportRecord record);

    List<ResumeExportRecord> listByVersion(@Param("versionId") Long versionId,
                                           @Param("userId") Long userId);

    List<ResumeExportSummary> summarizeByResume(@Param("resumeId") Long resumeId,
                                                @Param("userId") Long userId);
}
