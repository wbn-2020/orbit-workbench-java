package com.orbitworkbench.worklog.api;

import com.orbitworkbench.identity.application.OrbitUserDetails;
import com.orbitworkbench.worklog.api.KnowledgeCardDtos.KnowledgeCardResponse;
import com.orbitworkbench.worklog.api.WorkLogDtos.CreateWorkLogRequest;
import com.orbitworkbench.worklog.api.WorkLogDtos.WorkLogResponse;
import com.orbitworkbench.worklog.application.WorkLogService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作记录接口：列表、新增、蒸馏为知识卡片。
 * 所有读写边界由记录自身的 user_id 决定，别人的记录一律 404。
 */
@RestController
@RequestMapping("/api/v1/work-logs")
public class WorkLogController {

    private final WorkLogService service;

    public WorkLogController(WorkLogService service) {
        this.service = service;
    }

    @GetMapping
    public List<WorkLogResponse> list(Authentication authentication) {
        return service.list(userId(authentication));
    }

    @PostMapping
    public WorkLogResponse create(@Valid @RequestBody CreateWorkLogRequest request,
                                 Authentication authentication) {
        return service.create(userId(authentication), request);
    }

    @PostMapping("/{id}/distill")
    public KnowledgeCardResponse distill(@PathVariable Long id, Authentication authentication) {
        return service.distill(userId(authentication), id);
    }

    private Long userId(Authentication authentication) {
        return ((OrbitUserDetails) authentication.getPrincipal()).userId();
    }
}
