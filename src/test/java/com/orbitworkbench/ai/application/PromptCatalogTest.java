package com.orbitworkbench.ai.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.orbitworkbench.interview.application.InterviewReportService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.junit.jupiter.api.Test;

/**
 * 提示词外置的字节锁：prompt 移到 resources/prompts 后，任何对文件的改动
 * （哪怕只是首尾空白）都必须是有意的规则变更，否则面试报告的
 * SCORING_RULE_VERSION 与历史报告对不上、其余场景的系统提示也会漂移。
 *
 * <p>哈希基线取自迁移当天与旧 Java 文本块逐字节一致的提取结果。
 */
class PromptCatalogTest {

    @Test
    void scoringRuleVersionStaysAtMigratedBaseline() throws Exception {
        // 迁移前在库上生成的报告携带该版本；提示词字节若变，此断言即失败。
        // SCORING_RULE_VERSION 是 interview 包内常量，这里读取已加载类的静态字段。
        var field = InterviewReportService.class.getDeclaredField("SCORING_RULE_VERSION");
        field.setAccessible(true);
        assertEquals("rule-63f83552a782", field.get(null));
    }

    @Test
    void reportPromptBytesAreLocked() {
        assertEquals("c16ea178f532e2c06f4ac3af45741ab46e52b694cf01bb06a75dfeb10bd12fce",
                sha256(PromptCatalog.load("interview-report-system")));
    }

    @Test
    void knowledgeAnswerPromptBytesAreLocked() {
        assertEquals("0686bddd9cbba40b555a3d3f48f401e7b28cfd16c049faed9b7cb4075c2c1cd4",
                sha256(PromptCatalog.load("knowledge-answer-system")));
    }

    @Test
    void projectFactPromptBytesAreLocked() {
        assertEquals("5286de86c31024762d9b3e8c3291e8f216124969896112ff286fff0d533cd9e4",
                sha256(PromptCatalog.load("project-fact-system")));
    }

    @Test
    void worklogDistillPromptBytesAreLocked() {
        assertEquals("8ebe413147a7fa47170b342208e9ca0da690e7a1186051128f8a4d6d234b280a",
                sha256(PromptCatalog.load("worklog-distill-system")));
    }

    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
