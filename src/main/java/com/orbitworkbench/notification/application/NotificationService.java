package com.orbitworkbench.notification.application;

import com.orbitworkbench.notification.api.NotificationDtos.NotificationResponse;
import com.orbitworkbench.notification.domain.NotificationEvent;
import com.orbitworkbench.notification.domain.NotificationRecord;
import com.orbitworkbench.notification.infrastructure.mapper.NotificationMapper;
import com.orbitworkbench.preference.application.PreferenceService;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.shared.api.PageResult;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 通知中心：读侧（列表/未读数/标记已读）与写侧（业务事件落库）。
 * 写侧一律 best-effort——插入异常只记日志，绝不影响调用它的业务流程；
 * 幂等由 notification(user_id, idempotency_key) 唯一键 + INSERT IGNORE 保证。
 * “标记已读”仅置 read_at，不删除任何原始业务记录。
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final int MAX_TITLE = 128;
    private static final int MAX_CONTENT = 512;
    private static final int MAX_PAGE_SIZE = 50;

    public static final String RESOURCE_INTERVIEW_SESSION = "INTERVIEW_SESSION";
    public static final String RESOURCE_PROJECT_VERSION = "PROJECT_VERSION";
    public static final String RESOURCE_STUDY_TASK = "STUDY_TASK";

    private final NotificationMapper mapper;
    private final PreferenceService preferenceService;

    public NotificationService(NotificationMapper mapper, PreferenceService preferenceService) {
        this.mapper = mapper;
        this.preferenceService = preferenceService;
    }

    @Transactional(readOnly = true)
    public PageResult<NotificationResponse> list(Long userId, boolean unreadOnly, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int offset = (safePage - 1) * safeSize;
        List<NotificationResponse> items = mapper.listByUser(userId, unreadOnly, safeSize, offset)
                .stream().map(NotificationResponse::from).toList();
        int total = mapper.countByUser(userId, unreadOnly);
        return new PageResult<>(items, safePage, safeSize, total);
    }

    @Transactional(readOnly = true)
    public int unreadCount(Long userId) {
        return mapper.countByUser(userId, true);
    }

    @Transactional
    public void markRead(Long userId, Long id) {
        NotificationRecord record = mapper.findByIdAndUser(id, userId);
        if (record == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "通知不存在");
        }
        mapper.markRead(id, userId, Instant.now());
    }

    @Transactional
    public int markAllRead(Long userId) {
        return mapper.markAllRead(userId, Instant.now());
    }

    /**
     * 落库一条通知（幂等、best-effort）。resourceRoute 为空时前端按事件类型兜底跳转。
     */
    public void notify(NotificationEvent event, Long userId, String title, String content,
                       String resourceType, Long resourceId, String resourceRoute,
                       String idempotencyKey) {
        if (!preferenceService.isNotificationEnabled(userId, event)) {
            log.info("用户已关闭该类通知，跳过写入，event={} userId={} resourceId={}",
                    event, userId, resourceId);
            return;
        }
        try {
            NotificationRecord record = new NotificationRecord();
            record.setUserId(userId);
            record.setEventType(event);
            record.setTitle(truncate(title, MAX_TITLE));
            record.setContent(truncate(content == null ? "" : content, MAX_CONTENT));
            record.setResourceType(resourceType);
            record.setResourceId(resourceId);
            record.setResourceRoute(truncate(resourceRoute, 255));
            record.setIdempotencyKey(truncate(idempotencyKey, 160));
            record.setReadAt(null);
            record.setCreatedAt(Instant.now());
            mapper.insertIgnore(record);
        } catch (RuntimeException exception) {
            log.warn("写入通知失败，event={} userId={} resourceId={}",
                    event, userId, resourceId, exception);
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
