package com.orbitworkbench.jobprofile.application;

import com.orbitworkbench.jobprofile.api.JobProfileDtos.JobProfileRequest;
import com.orbitworkbench.jobprofile.api.JobProfileDtos.JobProfileResponse;
import com.orbitworkbench.jobprofile.api.JobProfileDtos.JobProfileStateResponse;
import com.orbitworkbench.jobprofile.domain.JobProfileRecord;
import com.orbitworkbench.jobprofile.infrastructure.mapper.JobProfileMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobProfileService {

    private final JobProfileMapper mapper;

    public JobProfileService(JobProfileMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public JobProfileStateResponse get(Long userId) {
        JobProfileRecord record = mapper.findByUserId(userId);
        return record == null ? JobProfileStateResponse.empty() : JobProfileStateResponse.from(record);
    }

    @Transactional
    public JobProfileResponse save(Long userId, JobProfileRequest request) {
        JobProfileRecord record = mapper.findByUserId(userId);
        Instant now = Instant.now();
        if (record == null) {
            record = new JobProfileRecord();
            record.setUserId(userId);
            record.setCreatedAt(now);
            record.setUpdatedAt(now);
            copy(request, record);
            mapper.insert(record);
            return JobProfileResponse.from(record);
        }

        copy(request, record);
        record.setUpdatedAt(now);
        if (mapper.update(record) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "求职档案已变化，请刷新后重试");
        }
        return JobProfileResponse.from(record);
    }

    private void copy(JobProfileRequest request, JobProfileRecord record) {
        record.setTargetRole(request.targetRole().trim());
        record.setTargetExperienceBand(request.targetExperienceBand());
        record.setCareerStage(request.careerStage());
        record.setTargetLevel(normalize(request.targetLevel()));
        record.setTargetCompany(normalize(request.targetCompany()));
        record.setJavaSkillLevel(request.javaSkillLevel());
        record.setAiSkillLevel(request.aiSkillLevel());
        record.setTargetInterviewDate(request.targetInterviewDate());
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
