package com.orbitworkbench.jobapplication.application;

import com.orbitworkbench.jobapplication.api.JobApplicationDtos.ApplicationListResponse;
import com.orbitworkbench.jobapplication.api.JobApplicationDtos.ApplicationResponse;
import com.orbitworkbench.jobapplication.api.JobApplicationDtos.CreateRequest;
import com.orbitworkbench.jobapplication.api.JobApplicationDtos.StageCountResponse;
import com.orbitworkbench.jobapplication.api.JobApplicationDtos.StageRequest;
import com.orbitworkbench.jobapplication.api.JobApplicationDtos.UpdateRequest;
import com.orbitworkbench.jobapplication.domain.ApplicationEventRecord;
import com.orbitworkbench.jobapplication.domain.ApplicationStage;
import com.orbitworkbench.jobapplication.domain.JobApplicationRecord;
import com.orbitworkbench.jobapplication.infrastructure.mapper.ApplicationEventMapper;
import com.orbitworkbench.jobapplication.infrastructure.mapper.JobApplicationMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 求职投递记录：创建、编辑、阶段推进（含流水）、归档与删除。
 * 阶段推进使用期望状态乐观并发控制；每次阶段变化写入 job_application_event 流水。
 * 不连接任何招聘网站，仅站内记录。
 */
@Service
public class JobApplicationService {

    private final JobApplicationMapper mapper;
    private final ApplicationEventMapper eventMapper;

    public JobApplicationService(JobApplicationMapper mapper, ApplicationEventMapper eventMapper) {
        this.mapper = mapper;
        this.eventMapper = eventMapper;
    }

    @Transactional
    public ApplicationResponse create(Long userId, CreateRequest request) {
        Instant now = Instant.now();
        JobApplicationRecord record = new JobApplicationRecord();
        record.setUserId(userId);
        record.setCompany(request.company().trim());
        record.setRole(request.role().trim());
        record.setJdSummary(normalize(request.jdSummary()));
        record.setSource(normalize(request.source()));
        record.setApplyDate(request.applyDate());
        record.setInterviewDate(request.interviewDate());
        record.setStage(ApplicationStage.valueOf(request.stage()));
        record.setSalaryNote(normalize(request.salaryNote()));
        record.setContact(normalize(request.contact()));
        record.setNote(normalize(request.note()));
        record.setArchived(false);
        record.setStageChangedAt(now);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        mapper.insert(record);

        ApplicationEventRecord event = new ApplicationEventRecord();
        event.setApplicationId(record.getId());
        event.setUserId(userId);
        event.setEventType("CREATED");
        event.setToStage(record.getStage());
        event.setCreatedAt(now);
        eventMapper.insert(event);
        return ApplicationResponse.from(record);
    }

    @Transactional(readOnly = true)
    public ApplicationListResponse list(Long userId, boolean includeArchived) {
        List<JobApplicationRecord> records = mapper.listByUser(userId, includeArchived);
        List<ApplicationResponse> items = records.stream().map(ApplicationResponse::from).toList();
        return new ApplicationListResponse(items, countStages(items));
    }

    @Transactional(readOnly = true)
    public ApplicationResponse get(Long userId, Long id) {
        return ApplicationResponse.from(owned(userId, id));
    }

    @Transactional
    public ApplicationResponse update(Long userId, Long id, UpdateRequest request) {
        JobApplicationRecord record = owned(userId, id);
        record.setCompany(request.company().trim());
        record.setRole(request.role().trim());
        record.setJdSummary(normalize(request.jdSummary()));
        record.setSource(normalize(request.source()));
        record.setApplyDate(request.applyDate());
        record.setInterviewDate(request.interviewDate());
        record.setResult(request.result() == null ? null
                : com.orbitworkbench.jobapplication.domain.ApplicationResult.valueOf(request.result()));
        record.setSalaryNote(normalize(request.salaryNote()));
        record.setContact(normalize(request.contact()));
        record.setNote(normalize(request.note()));
        record.setUpdatedAt(Instant.now());
        if (mapper.updateEditable(record) != 1) {
            throw conflict("投递记录已变化，请刷新后重试");
        }
        return ApplicationResponse.from(record);
    }

    @Transactional
    public ApplicationResponse advance(Long userId, Long id, StageRequest request) {
        JobApplicationRecord record = owned(userId, id);
        ApplicationStage target = ApplicationStage.valueOf(request.stage());
        if (target == record.getStage()) {
            throw conflict("阶段未变化：当前已是 " + target);
        }
        Instant now = Instant.now();
        int updated = mapper.updateStage(record.getId(), userId, record.getStage(), target, now, now);
        if (updated != 1) {
            throw conflict("投递记录已变化，请刷新后重试");
        }
        ApplicationEventRecord event = new ApplicationEventRecord();
        event.setApplicationId(record.getId());
        event.setUserId(userId);
        event.setEventType("STAGE_CHANGED");
        event.setFromStage(record.getStage());
        event.setToStage(target);
        event.setDetail(normalize(request.detail()));
        event.setCreatedAt(now);
        eventMapper.insert(event);
        return ApplicationResponse.from(mapper.findById(record.getId()));
    }

    @Transactional
    public ApplicationResponse setArchived(Long userId, Long id, boolean archived) {
        JobApplicationRecord record = owned(userId, id);
        Instant now = Instant.now();
        if (mapper.updateArchived(record.getId(), userId, archived, now) != 1) {
            throw conflict("投递记录已变化，请刷新后重试");
        }
        ApplicationEventRecord event = new ApplicationEventRecord();
        event.setApplicationId(record.getId());
        event.setUserId(userId);
        event.setEventType("ARCHIVED");
        event.setDetail(archived ? "归档" : "取消归档");
        event.setCreatedAt(now);
        eventMapper.insert(event);
        return ApplicationResponse.from(mapper.findById(record.getId()));
    }

    @Transactional
    public void delete(Long userId, Long id) {
        owned(userId, id);
        if (mapper.delete(id, userId) != 1) {
            throw conflict("投递记录已变化，请刷新后重试");
        }
    }

    private JobApplicationRecord owned(Long userId, Long id) {
        JobApplicationRecord record = mapper.findById(id);
        if (record == null || !record.getUserId().equals(userId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "投递记录不存在");
        }
        return record;
    }

    private StageCountResponse countStages(List<ApplicationResponse> items) {
        long watching = items.stream().filter(i -> "WATCHING".equals(i.stage())).count();
        long applied = items.stream().filter(i -> "APPLIED".equals(i.stage())).count();
        long writtenTest = items.stream().filter(i -> "WRITTEN_TEST".equals(i.stage())).count();
        long interviewing = items.stream().filter(i -> "INTERVIEWING".equals(i.stage())).count();
        long hr = items.stream().filter(i -> "HR".equals(i.stage())).count();
        long offer = items.stream().filter(i -> "OFFER".equals(i.stage())).count();
        long closed = items.stream().filter(i -> "CLOSED".equals(i.stage())).count();
        return new StageCountResponse(watching, applied, writtenTest, interviewing, hr, offer, closed);
    }

    private ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT, message);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
