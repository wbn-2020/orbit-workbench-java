package com.orbitworkbench.aiconnection.api;

import com.orbitworkbench.aiconnection.api.AiConnectionDtos.ConnectionRequest;
import com.orbitworkbench.aiconnection.api.AiConnectionDtos.ConnectionResponse;
import com.orbitworkbench.aiconnection.api.AiConnectionDtos.ConnectionTestResponse;
import com.orbitworkbench.aiconnection.api.AiConnectionDtos.DraftTestRequest;
import com.orbitworkbench.aiconnection.api.AiConnectionDtos.EnabledRequest;
import com.orbitworkbench.aiconnection.api.AiConnectionDtos.SavedTestRequest;
import com.orbitworkbench.aiconnection.api.AiConnectionDtos.UpdateConnectionRequest;
import com.orbitworkbench.aiconnection.application.AiConnectionService;
import com.orbitworkbench.shared.api.PageResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai-connections")
public class AiConnectionController {
    private final AiConnectionService service;

    public AiConnectionController(AiConnectionService service) {
        this.service = service;
    }

    @GetMapping
    public PageResult<ConnectionResponse> list(@RequestParam(required = false) Boolean enabled,
                                               @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return service.list(enabled, page, size);
    }

    @GetMapping("/{id}")
    public ConnectionResponse get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    public ConnectionResponse create(@Valid @RequestBody ConnectionRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public ConnectionResponse update(@PathVariable Long id,
                                     @Valid @RequestBody UpdateConnectionRequest request) {
        return service.update(id, request);
    }

    @PutMapping("/{id}/enabled")
    public ConnectionResponse enabled(@PathVariable Long id,
                                      @Valid @RequestBody EnabledRequest request) {
        return service.setEnabled(id, request.enabled(), request.expectedVersion());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) { service.delete(id); }

    @PostMapping("/test")
    public ConnectionTestResponse testDraft(@Valid @RequestBody DraftTestRequest request) {
        return service.testDraft(request);
    }

    @PostMapping("/{id}/test")
    public ConnectionTestResponse testSaved(@PathVariable Long id,
                                            @Valid @RequestBody SavedTestRequest request) {
        return service.testSaved(id, request);
    }
}
