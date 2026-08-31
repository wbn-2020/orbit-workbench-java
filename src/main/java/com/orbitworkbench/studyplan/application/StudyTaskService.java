package com.orbitworkbench.studyplan.application;

import com.orbitworkbench.interview.application.InterviewReportService;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.shared.api.RequestEnums;
import com.orbitworkbench.studyplan.api.StudyTaskDtos.CreateTaskRequest;
import com.orbitworkbench.studyplan.api.StudyTaskDtos.TaskResponse;
import com.orbitworkbench.studyplan.domain.StudyTaskPriority;
import com.orbitworkbench.studyplan.domain.StudyTaskRecord;
import com.orbitworkbench.studyplan.domain.StudyTaskSource;
import com.orbitworkbench.studyplan.domain.StudyTaskStatus;
import com.orbitworkbench.studyplan.infrastructure.mapper.StudyTaskMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 复习计划任务：手工创建、状态动作（开始/完成/延期/跳过）、删除与从报告生成。
 * 状态机按 PRD §10.4：PLANNED -> IN_PROGRESS -> COMPLETED；PLANNED -> POSTPONED / SKIPPED。
 * 报告生成任务来源为 REPORT + 报告 id，按标题幂等（自动生成不覆盖手工安排，只增量补充）。
 */
@Service
public class StudyTaskService {

    private final StudyTaskMapper mapper;
    private final InterviewReportService reportService;

    public StudyTaskService(StudyTaskMapper mapper, InterviewReportService reportService) {
        this.mapper = mapper;
        this.reportService = reportService;
    }

    @Transactional
    public TaskResponse create(Long userId, CreateTaskRequest request) {
        Instant now = Instant.now();
        StudyTaskRecord record = new StudyTaskRecord();
        record.setUserId(userId);
        record.setSourceType(StudyTaskSource.MANUAL);
        record.setTitle(request.title().trim());
        record.setTopic(normalize(request.topic()));
        record.setTaskType(normalize(request.taskType()));
        record.setPriority(RequestEnums.parse(StudyTaskPriority.class, request.priority(), "priority"));
        record.setEstimatedMinutes(request.estimatedMinutes());
        record.setDueDate(request.dueDate());
        record.setStatus(StudyTaskStatus.PLANNED);
        record.setManual(true);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        mapper.insert(record);
        return TaskResponse.from(record);
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> list(Long userId, String status) {
        StudyTaskStatus statusFilter = parseStatusOrNull(status);
        return mapper.listByUser(userId, statusFilter).stream()
                .map(TaskResponse::from)
                .toList();
    }

    @Transactional
    public TaskResponse start(Long userId, Long taskId) {
        StudyTaskRecord record = ownedTask(userId, taskId);
        requireStatus(record, StudyTaskStatus.PLANNED, "开始");
        updateStatus(record, StudyTaskStatus.IN_PROGRESS, null);
        return TaskResponse.from(reload(taskId));
    }

    @Transactional
    public TaskResponse complete(Long userId, Long taskId) {
        StudyTaskRecord record = ownedTask(userId, taskId);
        if (record.getStatus() != StudyTaskStatus.PLANNED
                && record.getStatus() != StudyTaskStatus.IN_PROGRESS) {
            throw conflict("仅计划中或进行中的任务可以完成：当前 " + record.getStatus());
        }
        updateStatus(record, StudyTaskStatus.COMPLETED, null);
        return TaskResponse.from(reload(taskId));
    }

    @Transactional
    public TaskResponse postpone(Long userId, Long taskId, LocalDate newDueDate) {
        StudyTaskRecord record = ownedTask(userId, taskId);
        requireStatus(record, StudyTaskStatus.PLANNED, "延期");
        updateStatus(record, StudyTaskStatus.POSTPONED, newDueDate);
        return TaskResponse.from(reload(taskId));
    }

    @Transactional
    public TaskResponse skip(Long userId, Long taskId) {
        StudyTaskRecord record = ownedTask(userId, taskId);
        requireStatus(record, StudyTaskStatus.PLANNED, "跳过");
        updateStatus(record, StudyTaskStatus.SKIPPED, null);
        return TaskResponse.from(reload(taskId));
    }

    @Transactional
    public void delete(Long userId, Long taskId) {
        ownedTask(userId, taskId);
        if (mapper.delete(taskId, userId) != 1) {
            throw conflict("任务已变化，请刷新后重试");
        }
    }

    /**
     * 从已就绪报告的 studySuggestions 生成复习任务：来源 REPORT + 会话对应报告，
     * 按标题幂等；返回新增数量。
     */
    @Transactional
    public int generateFromReport(Long userId, Long sessionId) {
        List<String> suggestions = reportService.readyStudySuggestions(userId, sessionId);
        if (suggestions.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "报告尚未生成复习建议（需要报告已就绪且包含 studySuggestions）");
        }
        Long reportId = reportService.reportIdOfSession(sessionId);
        int created = 0;
        for (String suggestion : suggestions) {
            String title = suggestion.trim();
            if (title.isEmpty()
                    || mapper.countBySourceTitle(userId, StudyTaskSource.REPORT, reportId, title) > 0) {
                continue;
            }
            Instant now = Instant.now();
            StudyTaskRecord record = new StudyTaskRecord();
            record.setUserId(userId);
            record.setSourceType(StudyTaskSource.REPORT);
            record.setSourceId(reportId);
            record.setTitle(title);
            record.setPriority(StudyTaskPriority.MEDIUM);
            record.setStatus(StudyTaskStatus.PLANNED);
            record.setManual(false);
            record.setCreatedAt(now);
            record.setUpdatedAt(now);
            mapper.insert(record);
            created += 1;
        }
        return created;
    }

    private StudyTaskRecord ownedTask(Long userId, Long taskId) {
        StudyTaskRecord record = mapper.findById(taskId);
        if (record == null || !record.getUserId().equals(userId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "复习任务不存在");
        }
        return record;
    }

    private StudyTaskRecord reload(Long taskId) {
        return mapper.findById(taskId);
    }

    private void requireStatus(StudyTaskRecord record, StudyTaskStatus expected, String action) {
        if (record.getStatus() != expected) {
            throw conflict("仅" + expected + "状态的任务可以" + action + "：当前 " + record.getStatus());
        }
    }

    private void updateStatus(StudyTaskRecord record, StudyTaskStatus target, LocalDate dueDate) {
        int updated = mapper.updateStatus(record.getId(), record.getUserId(), record.getStatus(),
                target, dueDate, Instant.now());
        if (updated != 1) {
            throw conflict("任务状态已变化，请刷新后重试");
        }
    }

    private StudyTaskStatus parseStatusOrNull(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return StudyTaskStatus.valueOf(status);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST,
                    "未知任务状态：" + status);
        }
    }

    private ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT, message);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
