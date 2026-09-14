package com.orbitworkbench.userfact.application;

import com.orbitworkbench.userfact.infrastructure.mapper.UserFactMapper;
import java.time.Instant;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 记忆注入打点（V46 用量治理）。
 *
 * <p>用 REQUIRES_NEW 独立提交：注入链路的读事务（{@code confirmedContext} 是 readOnly）
 * 不该因为打点变成写事务，业务失败也不该把「这条事实被用过」的记录一起回滚——
 * 与 {@code AiCallAuditRecorder} 同一动机。打点失败仅告警，不影响 AI 链路。
 */
@Service
public class MemoryUsageRecorder {

    private static final Logger log = LoggerFactory.getLogger(MemoryUsageRecorder.class);

    private final UserFactMapper factMapper;

    public MemoryUsageRecorder(UserFactMapper factMapper) {
        this.factMapper = factMapper;
    }

    /** 把本次实际进入请求的事实打点（id 可能来自编译快照的来源集）。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordInjection(Long userId, Collection<Long> factIds) {
        if (factIds == null || factIds.isEmpty()) {
            return;
        }
        try {
            factMapper.bumpInjection(userId, factIds, Instant.now());
        } catch (RuntimeException exception) {
            log.warn("记忆注入打点失败，userId={} 条数={}", userId, factIds.size(), exception);
        }
    }
}
