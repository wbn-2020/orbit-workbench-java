package com.orbitworkbench.learning.api;

import com.orbitworkbench.identity.application.OrbitUserDetails;
import com.orbitworkbench.learning.api.LearningGoalDtos.CreateLearningGoalRequest;
import com.orbitworkbench.learning.api.LearningGoalDtos.LearningGoalResponse;
import com.orbitworkbench.learning.api.LearningGoalDtos.UpdateLearningGoalRequest;
import com.orbitworkbench.learning.application.LearningGoalService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** 学习目标接口（v2 学习更新域）。暂无删除：数据删除规则未定（D-02），先以 DONE 关闭。 */
@RestController
@RequestMapping("/api/v1/learning-goals")
public class LearningGoalController {

    private final LearningGoalService service;

    public LearningGoalController(LearningGoalService service) {
        this.service = service;
    }

    @GetMapping
    public List<LearningGoalResponse> list(Authentication authentication) {
        return service.list(userId(authentication));
    }

    @PostMapping
    public LearningGoalResponse create(@Valid @RequestBody CreateLearningGoalRequest request,
                                       @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                       Authentication authentication) {
        return service.create(userId(authentication), request, idempotencyKey);
    }

    @PutMapping("/{id}")
    public LearningGoalResponse update(@PathVariable Long id,
                                       @Valid @RequestBody UpdateLearningGoalRequest request,
                                       Authentication authentication) {
        return service.update(userId(authentication), id, request);
    }

    private Long userId(Authentication authentication) {
        return ((OrbitUserDetails) authentication.getPrincipal()).userId();
    }
}
