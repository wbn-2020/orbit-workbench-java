package com.orbitworkbench.aiconnection.application;

import com.orbitworkbench.aiconnection.api.AiConnectionDtos.ConnectionResponse;
import com.orbitworkbench.aiconnection.api.AiScenarioDtos.CallAuditResponse;
import com.orbitworkbench.aiconnection.api.AiScenarioDtos.ScenarioRouteResponse;
import com.orbitworkbench.aiconnection.api.AiScenarioDtos.UpsertRouteRequest;
import com.orbitworkbench.aiconnection.domain.AiScenario;
import com.orbitworkbench.aiconnection.domain.CallAuditRecord;
import com.orbitworkbench.aiconnection.domain.ScenarioRouteRecord;
import com.orbitworkbench.aiconnection.infrastructure.mapper.AiScenarioMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.shared.api.PageResult;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 场景路由配置与调用审计查询。
 *
 * <p>路由按用户隔离；账户本身是全局资源，因此这里只校验存在与启用，不复制账户配置。
 */
@Service
public class AiScenarioService {

    private static final int MAX_PAGE_SIZE = 50;

    private final AiScenarioMapper mapper;
    private final AiConnectionService connectionService;

    public AiScenarioService(AiScenarioMapper mapper, AiConnectionService connectionService) {
        this.mapper = mapper;
        this.connectionService = connectionService;
    }

    /**
     * 四个场景的配置全景。未配置的场景回显默认策略实际会选中的账户，避免界面出现"空配置=不知道在用谁"。
     */
    @Transactional(readOnly = true)
    public List<ScenarioRouteResponse> list(Long userId) {
        Map<String, ScenarioRouteRecord> routes = mapper.listRoutes(userId).stream()
                .collect(Collectors.toMap(ScenarioRouteRecord::getScenarioCode, Function.identity(),
                        (left, right) -> left));
        ConnectionResponse defaultConnection = firstEnabled();
        return AiScenario.all().stream()
                .map(scenario -> {
                    ScenarioRouteRecord route = routes.get(scenario.name());
                    if (route != null) {
                        return ScenarioRouteResponse.configured(route, scenario.name(), scenario.label());
                    }
                    return ScenarioRouteResponse.unconfigured(
                            defaultConnection == null ? null : defaultConnection.id(),
                            defaultConnection == null ? null : defaultConnection.name(),
                            scenario.name(), scenario.label());
                })
                .toList();
    }

    @Transactional
    public ScenarioRouteResponse upsert(Long userId, AiScenario scenario, UpsertRouteRequest request) {
        requireUsable(request.primaryConnectionId(), "主用");
        if (request.backupConnectionId() != null) {
            if (request.backupConnectionId().equals(request.primaryConnectionId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                        "备用账户不能与主用账户相同");
            }
            requireUsable(request.backupConnectionId(), "备用");
        } else if (request.failoverEnabled()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "启用故障切换需要先选择备用账户");
        }
        Instant now = Instant.now();
        ScenarioRouteRecord existing = mapper.findRoute(userId, scenario.name());
        if (existing == null) {
            ScenarioRouteRecord record = new ScenarioRouteRecord();
            record.setUserId(userId);
            record.setScenarioCode(scenario.name());
            record.setPrimaryConnectionId(request.primaryConnectionId());
            record.setBackupConnectionId(request.backupConnectionId());
            record.setFailoverEnabled(request.failoverEnabled());
            record.setVersion(1);
            record.setCreatedAt(now);
            record.setUpdatedAt(now);
            mapper.insertRoute(record);
        } else {
            existing.setPrimaryConnectionId(request.primaryConnectionId());
            existing.setBackupConnectionId(request.backupConnectionId());
            existing.setFailoverEnabled(request.failoverEnabled());
            existing.setUpdatedAt(now);
            mapper.updateRoute(existing);
        }
        return ScenarioRouteResponse.configured(
                mapper.findRoute(userId, scenario.name()), scenario.name(), scenario.label());
    }

    @Transactional
    public void remove(Long userId, AiScenario scenario) {
        if (mapper.deleteRoute(userId, scenario.name()) == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND,
                    "该场景尚未配置账户路由");
        }
    }

    @Transactional(readOnly = true)
    public PageResult<CallAuditResponse> audits(Long userId, AiScenario scenario, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        String scenarioCode = scenario == null ? null : scenario.name();
        List<CallAuditResponse> items = mapper.listAudits(userId, scenarioCode, safeSize,
                        (safePage - 1) * safeSize)
                .stream().map(CallAuditResponse::from).toList();
        return new PageResult<>(items, safePage, safeSize, mapper.countAudits(userId, scenarioCode));
    }

    private ConnectionResponse firstEnabled() {
        PageResult<ConnectionResponse> page = connectionService.list(true, 1, 1);
        return page.items().isEmpty() ? null : page.items().get(0);
    }

    private void requireUsable(Long connectionId, String role) {
        ConnectionResponse connection = connectionService.get(connectionId);
        if (!connection.enabled()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    role + "账户「" + connection.name() + "」未启用");
        }
    }
}
