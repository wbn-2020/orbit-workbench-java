package com.orbitworkbench.interview.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ReportFailureAdvisorTest {

    @Test
    void blankReasonHasNoAdvice() {
        assertNull(ReportFailureAdvisor.advise(null, 0));
        assertNull(ReportFailureAdvisor.advise("  ", 0));
    }

    @Test
    void knownProviderCodesTranslateToPlainLanguage() {
        var rate = ReportFailureAdvisor.advise("面试报告调用失败：RATE_LIMITED", 0);
        assertEquals("模型服务商限流", rate.summary());
        assertTrue(rate.nextStep().contains("限流"));

        var timeout = ReportFailureAdvisor.advise("面试报告调用失败：REQUEST_TIMEOUT", 0);
        assertEquals("模型响应超时", timeout.summary());

        var auth = ReportFailureAdvisor.advise("面试报告调用失败：AUTHENTICATION_FAILED", 0);
        assertEquals("账户凭据被拒", auth.summary());
        assertTrue(auth.nextStep().contains("API Key"));
    }

    @Test
    void parseFailuresPointAtOutputJitter() {
        var json = ReportFailureAdvisor.advise("模型输出中没有 JSON 对象", 0);
        assertEquals("模型返回的评分结构不合规则", json.summary());
        var score = ReportFailureAdvisor.advise("totalScore 缺失或不在 0-100", 1);
        assertEquals("模型返回的评分结构不合规则", score.summary());
    }

    @Test
    void repeatedFailuresEscalateToSwitchAccount() {
        var fresh = ReportFailureAdvisor.advise("面试报告调用失败：UPSTREAM_UNAVAILABLE", 1);
        assertTrue(!fresh.nextStep().contains("已连续失败"));
        var repeated = ReportFailureAdvisor.advise("面试报告调用失败：UPSTREAM_UNAVAILABLE",
                ReportFailureAdvisor.SWITCH_AFTER_RETRIES);
        assertTrue(repeated.nextStep().contains("已连续失败 2 次"));
        assertTrue(repeated.nextStep().contains("换账户"));
    }

    @Test
    void unknownReasonFallsBackWithoutGuessing() {
        var unknown = ReportFailureAdvisor.advise("某种没见过的失败 500", 0);
        assertEquals("报告生成失败", unknown.summary());
        assertTrue(unknown.nextStep().contains("重试"));
    }
}
