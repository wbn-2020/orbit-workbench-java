package com.orbitworkbench.focus.application;

import com.orbitworkbench.focus.domain.FocusDayStat;
import com.orbitworkbench.focus.domain.FocusMode;
import com.orbitworkbench.focus.infrastructure.mapper.FocusSessionMapper;
import com.orbitworkbench.focus.domain.FocusSessionRecord;
import com.orbitworkbench.focus.domain.FocusSessionRow;
import com.orbitworkbench.preference.application.PreferenceService;
import com.orbitworkbench.focus.api.FocusDtos.FocusSessionResponse;
import com.orbitworkbench.focus.api.FocusDtos.FocusStatResponse;
import com.orbitworkbench.focus.api.FocusDtos.SaveFocusSessionRequest;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 专注计时（v2 专注计时域）。只持久化已发生的专注/休息时段，
 * 趋势统计由读侧按天聚合，不冗余落列。
 */
@Service
public class FocusSessionService {

    private static final int LIST_LIMIT = 500;
    private static final int DEFAULT_DAYS = 7;
    private static final int MAX_DAYS = 90;
    private static final Map<String, FocusMode> MODE_ALIASES = Map.of(
            "focus", FocusMode.FOCUS,
            "break", FocusMode.BREAK);

    private final FocusSessionMapper mapper;
    private final PreferenceService preferenceService;
    private final Clock clock;

    @Autowired
    public FocusSessionService(FocusSessionMapper mapper, PreferenceService preferenceService) {
        this(mapper, preferenceService, Clock.systemUTC());
    }

    FocusSessionService(FocusSessionMapper mapper, PreferenceService preferenceService, Clock clock) {
        this.mapper = mapper;
        this.preferenceService = preferenceService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<FocusSessionResponse> list(Long userId) {
        return mapper.listByUser(userId, LIST_LIMIT, 0).stream()
                .map(FocusSessionResponse::from)
                .toList();
    }

    @Transactional
    public FocusSessionResponse save(Long userId, SaveFocusSessionRequest request, String idempotencyKey) {
        FocusMode mode = parseMode(request.mode());
        Instant startedAt = parseInstant(request.startedAt());
        int duration = request.durationMinutes();
        if (duration < 1 || duration > 1440) {
            throw invalid("durationMinutes 必须在 1 到 1440 之间");
        }
        String label = trimToNull(request.label(), 255, "label");
        String normalizedIdempotencyKey = trimToNull(idempotencyKey, 128, "Idempotency-Key");
        Instant now = Instant.now(clock);
        Instant allowedEnd = now.plusSeconds(60);
        if (startedAt.isAfter(allowedEnd)) {
            throw invalid("startedAt 不能是未来时间");
        }
        if (startedAt.plusSeconds(duration * 60L).isAfter(allowedEnd)) {
            throw invalid("专注时段尚未结束，不能提前保存");
        }
        // 幂等重放必须复用同一请求体：同键但内容不同说明客户端复用了旧密钥，
        // 静默返回旧行会让用户以为新时段已保存，所以显式 409。
        if (normalizedIdempotencyKey != null) {
            FocusSessionRow existing = mapper.findByIdempotencyKey(userId, normalizedIdempotencyKey);
            if (existing != null) {
                if (!sameSession(existing, startedAt, duration, mode, label)) {
                    throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                            "该幂等键已用于保存另一条专注记录，请重置后重试");
                }
                return FocusSessionResponse.from(existing);
            }
        }
        FocusSessionRecord record = new FocusSessionRecord();
        record.setUserId(userId);
        record.setStartedAt(startedAt);
        record.setDurationMinutes(duration);
        record.setMode(mode);
        record.setLabel(label);
        record.setIdempotencyKey(normalizedIdempotencyKey);
        record.setVersion(1);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        mapper.insert(record);
        FocusSessionRow row = requireOwned(userId, record.getId());
        return FocusSessionResponse.from(row);
    }

    private static boolean sameSession(FocusSessionRow row, Instant startedAt, int duration,
                                       FocusMode mode, String label) {
        return row.getStartedAt().equals(startedAt)
                && row.getDurationMinutes() == duration
                && row.getMode() == mode
                && java.util.Objects.equals(row.getLabel(), label);
    }

    /**
     * 近 N 日专注趋势：以「今天」为终点补齐连续日期，无数据的日子补零，
     * 保证前端拿到的数组长度恒等于 days。
     */
    @Transactional(readOnly = true)
    public List<FocusStatResponse> stats(Long userId, String daysParam) {
        int days = parseDays(daysParam);
        ZoneId zone = preferenceService.timezone(userId);
        LocalDate today = LocalDate.now(clock.withZone(zone));
        Instant since = today.minusDays(days - 1).atStartOfDay(zone).toInstant();
        Map<LocalDate, FocusDayStat> byDate = new LinkedHashMap<>();
        for (FocusSessionRow session : mapper.listFocusSince(userId, since)) {
            LocalDate date = session.getStartedAt().atZone(zone).toLocalDate();
            FocusDayStat stat = byDate.computeIfAbsent(date, ignored -> {
                FocusDayStat created = new FocusDayStat();
                created.setDate(date);
                return created;
            });
            stat.setFocusMinutes(stat.getFocusMinutes() + session.getDurationMinutes());
            stat.setSessions(stat.getSessions() + 1);
        }
        List<FocusStatResponse> result = new ArrayList<>(days);
        for (int offset = 0; offset < days; offset++) {
            LocalDate date = today.minusDays(days - 1 - offset);
            FocusDayStat stat = byDate.get(date);
            result.add(new FocusStatResponse(
                    date.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    stat == null ? 0 : (int) stat.getFocusMinutes(),
                    stat == null ? 0 : (int) stat.getSessions()));
        }
        return result;
    }

    private FocusSessionRow requireOwned(Long userId, Long id) {
        FocusSessionRow row = mapper.findOwned(userId, id);
        if (row == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "专注时段不存在");
        }
        return row;
    }

    private static FocusMode parseMode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw invalid("mode 不能为空");
        }
        FocusMode mode = MODE_ALIASES.get(raw.trim().toLowerCase());
        if (mode == null) {
            throw invalid("mode 取值不合法，可选：focus、break");
        }
        return mode;
    }

    private static Instant parseInstant(String raw) {
        if (raw == null || raw.isBlank()) {
            throw invalid("startedAt 不能为空");
        }
        try {
            return Instant.parse(raw.trim());
        } catch (RuntimeException exception) {
            throw invalid("startedAt 格式不正确，请使用 ISO-8601 时间戳");
        }
    }

    private static int parseDays(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_DAYS;
        }
        int value;
        try {
            value = Integer.parseInt(raw.trim());
        } catch (NumberFormatException exception) {
            throw invalid("days 必须是整数");
        }
        if (value < 1) {
            throw invalid("days 必须大于 0");
        }
        return Math.min(value, MAX_DAYS);
    }

    private static String trimToNull(String raw, int maxChars, String field) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.length() > maxChars) {
            throw invalid(field + " 长度不得超过 " + maxChars + " 字");
        }
        return value;
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST, message);
    }
}
