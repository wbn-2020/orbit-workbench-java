package com.orbitworkbench.schedule.application;

import com.orbitworkbench.interview.domain.InterviewSessionRecord;
import com.orbitworkbench.interview.infrastructure.mapper.InterviewSessionMapper;
import com.orbitworkbench.jobapplication.domain.JobApplicationRecord;
import com.orbitworkbench.jobapplication.infrastructure.mapper.JobApplicationMapper;
import com.orbitworkbench.schedule.api.ScheduleDtos.AgendaItemResponse;
import com.orbitworkbench.schedule.api.ScheduleDtos.CreateScheduleRequest;
import com.orbitworkbench.schedule.api.ScheduleDtos.ScheduleEventResponse;
import com.orbitworkbench.schedule.api.ScheduleDtos.UpdateScheduleRequest;
import com.orbitworkbench.schedule.domain.ScheduleEventRecord;
import com.orbitworkbench.schedule.domain.ScheduleSourceType;
import com.orbitworkbench.schedule.domain.ScheduleStatus;
import com.orbitworkbench.schedule.infrastructure.mapper.ScheduleEventMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.studyplan.domain.StudyTaskRecord;
import com.orbitworkbench.studyplan.infrastructure.mapper.StudyTaskMapper;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 今日工作台日程：自定义日程落 schedule_event 表；面试/复习/投递为只读实时派生，
 * 聚合端点合并成一个按开始时间排序的清单，供工作台一次性读取（不再前端拼接）。
 */
@Service
public class ScheduleService {

    private final ScheduleEventMapper scheduleMapper;
    private final InterviewSessionMapper interviewMapper;
    private final StudyTaskMapper studyTaskMapper;
    private final JobApplicationMapper applicationMapper;

    public ScheduleService(ScheduleEventMapper scheduleMapper,
                           InterviewSessionMapper interviewMapper,
                           StudyTaskMapper studyTaskMapper,
                           JobApplicationMapper applicationMapper) {
        this.scheduleMapper = scheduleMapper;
        this.interviewMapper = interviewMapper;
        this.studyTaskMapper = studyTaskMapper;
        this.applicationMapper = applicationMapper;
    }

    @Transactional
    public ScheduleEventResponse createCustom(Long userId, CreateScheduleRequest request) {
        validateRange(request.startAt(), request.endAt());
        Instant now = Instant.now();
        ScheduleEventRecord record = new ScheduleEventRecord();
        record.setUserId(userId);
        record.setSourceType(ScheduleSourceType.CUSTOM);
        record.setSourceId(null);
        record.setTitle(request.title());
        record.setStartAt(request.startAt());
        record.setEndAt(request.endAt());
        record.setAllDay(request.allDay());
        record.setStatus(ScheduleStatus.PLANNED);
        record.setReminderMinutes(request.reminderMinutes());
        record.setResourceRoute(trimToNull(request.resourceRoute()));
        record.setNote(trimToNull(request.note()));
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        scheduleMapper.insert(record);
        return ScheduleEventResponse.from(record);
    }

    @Transactional
    public ScheduleEventResponse updateCustom(Long userId, Long id, UpdateScheduleRequest request) {
        ownedCustom(userId, id);
        validateRange(request.startAt(), request.endAt());
        scheduleMapper.update(id, userId, request.title(), request.startAt(), request.endAt(),
                request.allDay(), request.reminderMinutes(), trimToNull(request.resourceRoute()),
                trimToNull(request.note()), Instant.now());
        return ScheduleEventResponse.from(ownedCustom(userId, id));
    }

    @Transactional
    public ScheduleEventResponse setStatus(Long userId, Long id, ScheduleStatus status) {
        ownedCustom(userId, id);
        scheduleMapper.updateStatus(id, userId, status, Instant.now());
        return ScheduleEventResponse.from(ownedCustom(userId, id));
    }

    @Transactional
    public void deleteCustom(Long userId, Long id) {
        ownedCustom(userId, id);
        scheduleMapper.delete(id, userId);
    }

    @Transactional(readOnly = true)
    public List<AgendaItemResponse> agenda(Long userId, Instant from, Instant to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "日程查询需要有效的起止时间");
        }
        List<AgendaItemResponse> items = new ArrayList<>();

        for (ScheduleEventRecord custom : scheduleMapper.listCustomBetween(userId, from, to)) {
            items.add(new AgendaItemResponse(
                    custom.getSourceType().name(), custom.getId(), custom.getTitle(),
                    custom.getStartAt(), custom.getEndAt(),
                    Boolean.TRUE.equals(custom.getAllDay()), custom.getStatus().name(),
                    custom.getResourceRoute()));
        }

        for (InterviewSessionRecord session : interviewMapper.listScheduledBetween(userId, from, to)) {
            Instant start = session.getScheduledAt();
            Instant end = session.getDurationLimitMinutes() == null
                    ? null : start.plus(Duration.ofMinutes(session.getDurationLimitMinutes()));
            items.add(new AgendaItemResponse(
                    ScheduleSourceType.INTERVIEW.name(), session.getId(), session.getTitle(),
                    start, end, false, ScheduleStatus.PLANNED.name(),
                    "/interviews/" + session.getId()));
        }

        LocalDate fromDate = LocalDate.ofInstant(from, ZoneOffset.UTC);
        LocalDate toDate = LocalDate.ofInstant(to, ZoneOffset.UTC);
        for (StudyTaskRecord task : studyTaskMapper.listActiveByDueRange(userId, fromDate, toDate)) {
            Instant start = task.getDueDate().atStartOfDay(ZoneOffset.UTC).toInstant();
            items.add(new AgendaItemResponse(
                    ScheduleSourceType.STUDY_TASK.name(), task.getId(), task.getTitle(),
                    start, start, true, ScheduleStatus.PLANNED.name(), "/study-plan"));
        }

        for (JobApplicationRecord application
                : applicationMapper.listWithInterviewDateBetween(userId, fromDate, toDate)) {
            Instant start = application.getInterviewDate().atStartOfDay(ZoneOffset.UTC).toInstant();
            String title = (application.getCompany() == null ? "" : application.getCompany())
                    + (application.getRole() == null || application.getCompany() == null
                        ? "" : " · ") + (application.getRole() == null ? "" : application.getRole());
            items.add(new AgendaItemResponse(
                    ScheduleSourceType.APPLICATION.name(), application.getId(),
                    title.isBlank() ? "面试跟进" : title,
                    start, start, true, ScheduleStatus.PLANNED.name(), "/applications"));
        }

        items.sort(Comparator.comparing(AgendaItemResponse::startAt)
                .thenComparing(AgendaItemResponse::sourceType));
        return items;
    }

    private ScheduleEventRecord ownedCustom(Long userId, Long id) {
        ScheduleEventRecord record = scheduleMapper.findByIdAndUser(id, userId);
        if (record == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "日程不存在");
        }
        if (record.getSourceType() != ScheduleSourceType.CUSTOM) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "系统派生日程不可修改，请在来源处调整");
        }
        return record;
    }

    private void validateRange(Instant start, Instant end) {
        if (end != null && !end.isAfter(start)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "结束时间必须晚于开始时间");
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
