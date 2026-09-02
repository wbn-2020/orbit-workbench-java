package com.orbitworkbench.preference.application;

import com.orbitworkbench.notification.domain.NotificationEvent;
import com.orbitworkbench.preference.api.PreferenceDtos.PreferenceRequest;
import com.orbitworkbench.preference.api.PreferenceDtos.PreferenceResponse;
import com.orbitworkbench.preference.domain.UserPreferenceRecord;
import com.orbitworkbench.preference.infrastructure.mapper.UserPreferenceMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PreferenceService {

    private static final Logger log = LoggerFactory.getLogger(PreferenceService.class);
    private static final String DEFAULT_TIMEZONE = "Asia/Shanghai";

    private final UserPreferenceMapper mapper;

    public PreferenceService(UserPreferenceMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional
    public PreferenceResponse get(Long userId) {
        UserPreferenceRecord record = ensureRecord(userId);
        return PreferenceResponse.from(record);
    }

    @Transactional
    public PreferenceResponse save(Long userId, PreferenceRequest request) {
        validateTimezone(request.timezoneId());
        UserPreferenceRecord current = ensureRecord(userId);
        if (current.getVersion() != request.expectedVersion()) {
            throw conflict();
        }

        current.setNotifyReportReady(request.notifyReportReady());
        current.setNotifyStudyDue(request.notifyStudyDue());
        current.setNotifyInterview(request.notifyInterview());
        current.setNotifyImportFailure(request.notifyImportFailure());
        current.setNotifyAiFailure(request.notifyAiFailure());
        current.setTimezoneId(request.timezoneId());
        current.setUpdatedAt(Instant.now());

        if (mapper.update(current, request.expectedVersion()) != 1) {
            throw conflict();
        }
        return PreferenceResponse.from(requireRecord(userId));
    }

    /**
     * Notification is best-effort. If the preference row cannot be read, retain the
     * historical default-on behavior rather than dropping a business notification.
     */
    @Transactional(readOnly = true)
    public boolean isNotificationEnabled(Long userId, NotificationEvent event) {
        UserPreferenceRecord record;
        try {
            record = mapper.findByUserId(userId);
        } catch (RuntimeException exception) {
            log.warn("读取通知偏好失败，按默认开启处理，event={} userId={}", event, userId, exception);
            return true;
        }
        if (record == null) {
            return true;
        }
        return switch (event) {
            case INTERVIEW_REPORT_READY -> record.isNotifyReportReady();
            case INTERVIEW_REPORT_FAILED, KNOWLEDGE_BUILD_FAILED -> record.isNotifyAiFailure();
            case PROJECT_IMPORT_PARTIAL -> record.isNotifyImportFailure();
            case STUDY_TASK_DUE -> record.isNotifyStudyDue();
            case SCHEDULE_REMINDER -> record.isNotifyInterview();
        };
    }

    @Transactional(readOnly = true)
    public ZoneId timezone(Long userId) {
        try {
            UserPreferenceRecord record = mapper.findByUserId(userId);
            if (record == null || record.getTimezoneId() == null || record.getTimezoneId().isBlank()) {
                return ZoneId.of(DEFAULT_TIMEZONE);
            }
            return ZoneId.of(record.getTimezoneId());
        } catch (RuntimeException exception) {
            log.warn("读取用户时区失败，按默认时区处理，userId={}", userId, exception);
            return ZoneId.of(DEFAULT_TIMEZONE);
        }
    }

    private UserPreferenceRecord ensureRecord(Long userId) {
        UserPreferenceRecord record = mapper.findByUserId(userId);
        if (record != null) {
            return record;
        }
        Instant now = Instant.now();
        mapper.insertIfAbsent(userId, now);
        return requireRecord(userId);
    }

    private UserPreferenceRecord requireRecord(Long userId) {
        UserPreferenceRecord record = mapper.findByUserId(userId);
        if (record == null) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    ErrorCode.UNKNOWN_PROVIDER_ERROR,
                    "用户偏好初始化失败");
        }
        return record;
    }

    private static void validateTimezone(String timezoneId) {
        try {
            ZoneId.of(timezoneId);
        } catch (DateTimeException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.INVALID_REQUEST,
                    "timezoneId 不是合法的 IANA 时区标识");
        }
    }

    private static ApiException conflict() {
        return new ApiException(
                HttpStatus.CONFLICT,
                ErrorCode.STATE_CONFLICT,
                "设置已在其他设备更新，请重新加载后再保存");
    }
}
