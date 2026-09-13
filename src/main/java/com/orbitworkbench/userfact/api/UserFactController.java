package com.orbitworkbench.userfact.api;

import com.orbitworkbench.identity.application.OrbitUserDetails;
import com.orbitworkbench.userfact.api.UserFactDtos.ConfirmUserFactRequest;
import com.orbitworkbench.userfact.api.UserFactDtos.CreateUserFactRequest;
import com.orbitworkbench.userfact.api.UserFactDtos.DistillResultResponse;
import com.orbitworkbench.userfact.api.UserFactDtos.UserFactListResponse;
import com.orbitworkbench.userfact.api.UserFactDtos.UserFactResponse;
import com.orbitworkbench.userfact.application.UserFactService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 用户画像事实接口：列表、手动新增、确认、归档与「生成建议」蒸馏。 */
@RestController
@RequestMapping("/api/v1/user-facts")
public class UserFactController {

    private final UserFactService service;

    public UserFactController(UserFactService service) {
        this.service = service;
    }

    @GetMapping
    public UserFactListResponse list(Authentication authentication) {
        return service.list(userId(authentication));
    }

    @PostMapping
    public UserFactResponse create(@Valid @RequestBody CreateUserFactRequest request,
                                   Authentication authentication) {
        return service.createManual(userId(authentication), request);
    }

    /** AI 蒸馏入口：同步调用模型，耗时可能到分钟级；连接可指定，未指定走场景路由。 */
    @PostMapping("/distill")
    public DistillResultResponse distill(@RequestParam(required = false) Long connectionId,
                                         Authentication authentication) {
        return service.distill(userId(authentication), connectionId);
    }

    @PutMapping("/{id}/confirm")
    public UserFactResponse confirm(@PathVariable Long id,
                                    @Valid @RequestBody ConfirmUserFactRequest request,
                                    Authentication authentication) {
        return service.confirm(userId(authentication), id, request);
    }

    @PostMapping("/{id}/archive")
    public UserFactResponse archive(@PathVariable Long id, Authentication authentication) {
        return service.archive(userId(authentication), id);
    }

    private Long userId(Authentication authentication) {
        return ((OrbitUserDetails) authentication.getPrincipal()).userId();
    }
}
