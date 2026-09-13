package com.orbitworkbench.userfact.api;

import com.orbitworkbench.identity.application.OrbitUserDetails;
import com.orbitworkbench.userfact.api.UserFactDtos.DigestResponse;
import com.orbitworkbench.userfact.application.ProfileDigestService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 画像编译快照接口（V44）：查看当前快照、手动触发编译、删除快照。
 * 编译是同步 AI 调用（PROFILE_DIGEST 场景），耗时可到分钟级，与蒸馏同一口径。
 */
@RestController
@RequestMapping("/api/v1/user-facts/digest")
public class ProfileDigestController {

    private final ProfileDigestService service;

    public ProfileDigestController(ProfileDigestService service) {
        this.service = service;
    }

    /** 当前快照；从未编译过返回 204。 */
    @GetMapping
    public ResponseEntity<DigestResponse> current(Authentication authentication) {
        DigestResponse digest = service.current(userId(authentication));
        return digest == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(digest);
    }

    /** 编译：把全部 CONFIRMED 事实压成一份高密度画像快照。 */
    @PostMapping
    public DigestResponse compile(@RequestParam(required = false) Long connectionId,
                                  Authentication authentication) {
        return service.digest(userId(authentication), connectionId);
    }

    /** 删除快照：注入链路回到逐条事实模式。 */
    @DeleteMapping
    public ResponseEntity<Void> clear(Authentication authentication) {
        service.clear(userId(authentication));
        return ResponseEntity.noContent().build();
    }

    private Long userId(Authentication authentication) {
        return ((OrbitUserDetails) authentication.getPrincipal()).userId();
    }
}
