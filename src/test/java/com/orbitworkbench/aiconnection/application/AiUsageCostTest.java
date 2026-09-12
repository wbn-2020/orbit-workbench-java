package com.orbitworkbench.aiconnection.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.orbitworkbench.aiconnection.application.AiConnectionService.AiConnectionRuntimeConfig;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * 成本计算的诚实性：算得出才给数字，算不出给 null——
 * 不能让「没有单价 / 上游没报 token」显示成 0 元，否则用户会以为免费。
 */
class AiUsageCostTest {

    private AiConnectionRuntimeConfig connection(BigDecimal in, BigDecimal out) {
        return new AiConnectionRuntimeConfig(1L, 2L, "主账户", "https://gw.example/v1",
                "/chat/completions", "CHAT_COMPLETIONS", "gpt-x", "sk-test", 30000,
                com.orbitworkbench.ai.application.WebSearchDialect.NONE, in, out);
    }

    @Test
    void computesCostFromTokensAndPerMillionPrices() {
        // 100 万输入 token × 2 元/百万 = 2 元；50 万输出 × 8 元/百万 = 4 元
        BigDecimal cost = AiCallAuditRecorder.cost(connection(new BigDecimal("2"), new BigDecimal("8")),
                1_000_000, 500_000);
        assertEquals(0, new BigDecimal("6").compareTo(cost));
    }

    @Test
    void returnsNullWhenNoPricingConfigured() {
        assertNull(AiCallAuditRecorder.cost(connection(null, null), 1000, 500));
    }

    @Test
    void returnsNullWhenProviderReportedNoTokens() {
        assertNull(AiCallAuditRecorder.cost(connection(new BigDecimal("2"), new BigDecimal("8")),
                null, null));
    }

    @Test
    void halfPricedConnectionStillComputesPartialCost() {
        // 只配输入价：仍按其可算的部分给金额，不因为缺一项价格就整体放弃
        BigDecimal cost = AiCallAuditRecorder.cost(connection(new BigDecimal("2"), null),
                1_000_000, 500_000);
        assertEquals(0, new BigDecimal("2").compareTo(cost));
    }

    @Test
    void roundsToSixDecimalPlaces() {
        BigDecimal cost = AiCallAuditRecorder.cost(connection(new BigDecimal("1"), BigDecimal.ZERO),
                1, null);
        // 1 token × 1 元/百万 = 0.000001
        assertEquals(0, new BigDecimal("0.000001").compareTo(cost));
    }
}
