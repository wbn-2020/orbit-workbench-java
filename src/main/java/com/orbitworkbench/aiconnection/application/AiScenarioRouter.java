package com.orbitworkbench.aiconnection.application;

import com.orbitworkbench.aiconnection.api.AiConnectionDtos.ConnectionResponse;
import com.orbitworkbench.aiconnection.application.AiConnectionService.AiConnectionRuntimeConfig;
import com.orbitworkbench.aiconnection.domain.AiScenario;
import com.orbitworkbench.aiconnection.domain.ScenarioRouteRecord;
import com.orbitworkbench.aiconnection.infrastructure.mapper.AiScenarioMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.shared.api.PageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 场景级 AI 账户选择：会话/请求指定的账户 &gt; 场景路由主用 &gt; 回退第一个已启用账户。
 *
 * <p>主用账户不可用时，若已配置可用备用账户则提升为当前账户；两者都不可用才回退默认策略，
 * 保证既有"未配置场景也能出题"的行为不被破坏。备用账户仅在故障切换白名单错误后使用。
 */
@Service
public class AiScenarioRouter {

    /** 账户来源，用于调用审计的配置快照。 */
    public static final String SOURCE_PINNED = "PINNED";
    public static final String SOURCE_ROUTE = "ROUTE";
    public static final String SOURCE_DEFAULT = "DEFAULT";

    private static final Logger log = LoggerFactory.getLogger(AiScenarioRouter.class);

    private final AiScenarioMapper mapper;
    private final AiConnectionService connectionService;

    public AiScenarioRouter(AiScenarioMapper mapper, AiConnectionService connectionService) {
        this.mapper = mapper;
        this.connectionService = connectionService;
    }

    public record ResolvedRoute(AiConnectionRuntimeConfig primary,
                                AiConnectionRuntimeConfig backup,
                                boolean failoverEnabled,
                                String source) {

        public boolean canFailover() {
            return failoverEnabled && backup != null
                    && !backup.connectionId().equals(primary.connectionId());
        }
    }

    @Transactional(readOnly = true)
    public ResolvedRoute resolve(Long userId, AiScenario scenario) {
        return resolve(userId, scenario, null);
    }

    @Transactional(readOnly = true)
    public ResolvedRoute resolve(Long userId, AiScenario scenario, Long pinnedConnectionId) {
        if (pinnedConnectionId != null) {
            // 显式指定（面试会话快照、请求内选择）优先，且不做故障切换，避免用户选定的账户被悄悄替换。
            return new ResolvedRoute(connectionService.getRuntimeConfig(pinnedConnectionId),
                    null, false, SOURCE_PINNED);
        }
        ScenarioRouteRecord route = mapper.findRoute(userId, scenario.name());
        if (route != null) {
            AiConnectionRuntimeConfig primary = tryRuntimeConfig(route.getPrimaryConnectionId());
            AiConnectionRuntimeConfig backup = tryRuntimeConfig(route.getBackupConnectionId());
            if (primary == null && backup != null) {
                log.warn("场景 {} 的主用账户 {} 不可用，本次提升备用账户 {} 为主用",
                        scenario, route.getPrimaryConnectionId(), route.getBackupConnectionId());
                return new ResolvedRoute(backup, null, false, SOURCE_ROUTE);
            }
            if (primary != null) {
                return new ResolvedRoute(primary, backup, route.isFailoverEnabled(), SOURCE_ROUTE);
            }
            log.warn("场景 {} 配置的主备账户均不可用，回退默认账户选择", scenario);
        }
        AiConnectionRuntimeConfig fallback = defaultConnection();
        return new ResolvedRoute(fallback, null, false, SOURCE_DEFAULT);
    }

    /** 既有默认策略：最近更新且已启用的第一个账户。 */
    private AiConnectionRuntimeConfig defaultConnection() {
        PageResult<ConnectionResponse> page = connectionService.list(true, 1, 1);
        if (page.items().isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "未配置可用的 AI 账户，请先在 AI 账户中添加并启用");
        }
        return connectionService.getRuntimeConfig(page.items().get(0).id());
    }

    private AiConnectionRuntimeConfig tryRuntimeConfig(Long connectionId) {
        if (connectionId == null) {
            return null;
        }
        try {
            return connectionService.getRuntimeConfig(connectionId);
        } catch (ApiException exception) {
            return null;
        }
    }
}
