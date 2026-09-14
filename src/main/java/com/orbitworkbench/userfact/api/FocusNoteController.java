package com.orbitworkbench.userfact.api;

import com.orbitworkbench.identity.application.OrbitUserDetails;
import com.orbitworkbench.userfact.api.UserFactDtos.FocusNoteResponse;
import com.orbitworkbench.userfact.api.UserFactDtos.SaveFocusNoteRequest;
import com.orbitworkbench.userfact.application.FocusNoteService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 近期关注接口（V46）：查看 / 保存 / 清除。每用户至多一条，保存即覆盖。
 * 与画像事实分开——「这周在准备什么」是短周期信号，不该写进长期画像。
 */
@RestController
@RequestMapping("/api/v1/user-facts/focus")
public class FocusNoteController {

    private final FocusNoteService service;

    public FocusNoteController(FocusNoteService service) {
        this.service = service;
    }

    /** 当前关注；从未设置返回 204。 */
    @GetMapping
    public ResponseEntity<FocusNoteResponse> current(Authentication authentication) {
        FocusNoteResponse note = service.current(userId(authentication));
        return note == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(note);
    }

    @PutMapping
    public FocusNoteResponse save(@Valid @RequestBody SaveFocusNoteRequest request,
                                  Authentication authentication) {
        return service.save(userId(authentication), request.content(), request.expiresInDays());
    }

    @DeleteMapping
    public ResponseEntity<Void> clear(Authentication authentication) {
        service.clear(userId(authentication));
        return ResponseEntity.noContent().build();
    }

    private Long userId(Authentication authentication) {
        return ((OrbitUserDetails) authentication.getPrincipal()).userId();
    }
}
