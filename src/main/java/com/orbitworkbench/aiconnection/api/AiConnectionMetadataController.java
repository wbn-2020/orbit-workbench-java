package com.orbitworkbench.aiconnection.api;

import com.orbitworkbench.aiconnection.api.AiConnectionMetadataDtos.ConnectionTestResponse;
import com.orbitworkbench.aiconnection.api.AiConnectionMetadataDtos.ModelProfileResponse;
import com.orbitworkbench.aiconnection.application.AiConnectionMetadataService;
import com.orbitworkbench.shared.api.PageResult;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai-connections")
public class AiConnectionMetadataController {

    private final AiConnectionMetadataService service;

    public AiConnectionMetadataController(AiConnectionMetadataService service) {
        this.service = service;
    }

    @GetMapping("/{id}/model-profiles")
    public List<ModelProfileResponse> modelProfiles(@PathVariable Long id) {
        return service.modelProfiles(id);
    }

    @GetMapping("/{id}/tests")
    public PageResult<ConnectionTestResponse> testHistory(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.testHistory(id, page, size);
    }
}
