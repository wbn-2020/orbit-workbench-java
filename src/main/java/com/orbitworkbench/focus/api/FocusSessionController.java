package com.orbitworkbench.focus.api;

import com.orbitworkbench.identity.application.OrbitUserDetails;
import com.orbitworkbench.focus.api.FocusDtos.FocusSessionResponse;
import com.orbitworkbench.focus.api.FocusDtos.SaveFocusSessionRequest;
import com.orbitworkbench.focus.application.FocusSessionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** 专注时段接口（v2 专注计时域）。 */
@RestController
@RequestMapping("/api/v1/focus-sessions")
public class FocusSessionController {

    private final FocusSessionService service;

    public FocusSessionController(FocusSessionService service) {
        this.service = service;
    }

    @GetMapping
    public List<FocusSessionResponse> list(Authentication authentication) {
        return service.list(userId(authentication));
    }

    @PostMapping
    public FocusSessionResponse save(@Valid @RequestBody SaveFocusSessionRequest request,
                                    @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                    Authentication authentication) {
        return service.save(userId(authentication), request, idempotencyKey);
    }

    private Long userId(Authentication authentication) {
        return ((OrbitUserDetails) authentication.getPrincipal()).userId();
    }
}
