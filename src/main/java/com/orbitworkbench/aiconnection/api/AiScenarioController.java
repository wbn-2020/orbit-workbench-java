package com.orbitworkbench.aiconnection.api;

import com.orbitworkbench.aiconnection.api.AiScenarioDtos.CallAuditResponse;
import com.orbitworkbench.aiconnection.api.AiScenarioDtos.ScenarioRouteResponse;
import com.orbitworkbench.aiconnection.api.AiScenarioDtos.UpsertRouteRequest;
import com.orbitworkbench.aiconnection.application.AiScenarioService;
import com.orbitworkbench.aiconnection.domain.AiScenario;
import com.orbitworkbench.identity.application.OrbitUserDetails;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.shared.api.PageResult;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai-scenarios")
public class AiScenarioController {

    private final AiScenarioService service;

    public AiScenarioController(AiScenarioService service) {
        this.service = service;
    }

    @GetMapping
    public List<ScenarioRouteResponse> list(Authentication authentication) {
        return service.list(userId(authentication));
    }

    @PutMapping("/{scenario}")
    public ScenarioRouteResponse upsert(@PathVariable String scenario,
                                        @Valid @RequestBody UpsertRouteRequest request,
                                        Authentication authentication) {
        return service.upsert(userId(authentication), parse(scenario), request);
    }

    @DeleteMapping("/{scenario}")
    public void remove(@PathVariable String scenario, Authentication authentication) {
        service.remove(userId(authentication), parse(scenario));
    }

    @GetMapping("/audits")
    public PageResult<CallAuditResponse> audits(
            @RequestParam(required = false) String scenario,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        AiScenario target = scenario == null || scenario.isBlank() ? null : parse(scenario);
        return service.audits(userId(authentication), target, page, size);
    }

    /** 单次调用下钻（V43）。 */
    @GetMapping("/audits/{id}")
    public com.orbitworkbench.aiconnection.api.AiScenarioDtos.CallAuditDetailResponse auditDetail(
            @PathVariable Long id, Authentication authentication) {
        return service.auditDetail(userId(authentication), id);
    }

    private AiScenario parse(String scenario) {
        return AiScenario.parse(scenario).orElseThrow(() -> new ApiException(
                HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                "未知的 AI 场景，可选值：" + String.join("、",
                        Arrays.stream(AiScenario.values()).map(Enum::name).toList())));
    }

    private Long userId(Authentication authentication) {
        return ((OrbitUserDetails) authentication.getPrincipal()).userId();
    }
}
