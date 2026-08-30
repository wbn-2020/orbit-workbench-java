package com.orbitworkbench.notification.api;

import com.orbitworkbench.identity.application.OrbitUserDetails;
import com.orbitworkbench.notification.api.NotificationDtos.MarkReadResponse;
import com.orbitworkbench.notification.api.NotificationDtos.NotificationResponse;
import com.orbitworkbench.notification.api.NotificationDtos.UnreadCountResponse;
import com.orbitworkbench.notification.application.NotificationService;
import com.orbitworkbench.shared.api.PageResult;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public PageResult<NotificationResponse> list(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        return service.list(userId(authentication), unreadOnly, page, size);
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount(Authentication authentication) {
        return new UnreadCountResponse(service.unreadCount(userId(authentication)));
    }

    @PostMapping("/{id}/read")
    public void markRead(@PathVariable Long id, Authentication authentication) {
        service.markRead(userId(authentication), id);
    }

    @PostMapping("/read-all")
    public MarkReadResponse markAllRead(Authentication authentication) {
        return new MarkReadResponse(service.markAllRead(userId(authentication)));
    }

    private Long userId(Authentication authentication) {
        return ((OrbitUserDetails) authentication.getPrincipal()).userId();
    }
}
