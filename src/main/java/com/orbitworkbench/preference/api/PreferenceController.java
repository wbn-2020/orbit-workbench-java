package com.orbitworkbench.preference.api;

import com.orbitworkbench.identity.application.OrbitUserDetails;
import com.orbitworkbench.preference.api.PreferenceDtos.PreferenceRequest;
import com.orbitworkbench.preference.api.PreferenceDtos.PreferenceResponse;
import com.orbitworkbench.preference.application.PreferenceService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/preferences")
public class PreferenceController {

    private final PreferenceService service;

    public PreferenceController(PreferenceService service) {
        this.service = service;
    }

    @GetMapping
    public PreferenceResponse get(Authentication authentication) {
        return service.get(userId(authentication));
    }

    @PutMapping
    public PreferenceResponse save(@Valid @RequestBody PreferenceRequest request,
                                   Authentication authentication) {
        return service.save(userId(authentication), request);
    }

    private Long userId(Authentication authentication) {
        return ((OrbitUserDetails) authentication.getPrincipal()).userId();
    }
}
