package com.orbitworkbench.ai.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AiOutputCleanerTest {

    private static final int MAX = 800;

    @Test
    void stripsLabelNumberingAndMarkdownPrefixes() {
        assertEquals("请解释 ConcurrentHashMap 的 size() 如何保证准确？",
                AiOutputCleaner.cleanQuestion("追问：**1. 请解释 ConcurrentHashMap 的 size() 如何保证准确？**",
                        MAX));
        assertEquals("线程池参数怎么定？",
                AiOutputCleaner.cleanQuestion("## 问题：线程池参数怎么定？", MAX));
        assertEquals("你如何保证库存不强一致？",
                AiOutputCleaner.cleanQuestion("- 第2问：你如何保证库存不强一致？", MAX));
    }

    @Test
    void unwrapsCodeFenceAndSurroundingQuotes() {
        assertEquals("Redis 过期键有哪些删除策略？",
                AiOutputCleaner.cleanQuestion("```json\nRedis 过期键有哪些删除策略？\n```", MAX));
        assertEquals("你负责哪一块？",
                AiOutputCleaner.cleanQuestion("“你负责哪一块？”", MAX));
    }

    @Test
    void keepsOnlyFirstQuestionWhenModelEmitsAList() {
        String raw = "1. 秒杀库存如何防超卖？\n2. 热点 Key 怎么打散？\n3. 降级方案是什么？";
        assertEquals("秒杀库存如何防超卖？", AiOutputCleaner.cleanQuestion(raw, MAX));
    }

    @Test
    void joinsWrappedLinesOfASingleQuestion() {
        String raw = "请说明你在项目中\n如何设计幂等键？";
        assertEquals("请说明你在项目中 如何设计幂等键？", AiOutputCleaner.cleanQuestion(raw, MAX));
    }

    @Test
    void truncatesAtSentenceBoundaryRatherThanMidSentence() {
        String longQuestion = "请说明。".repeat(60) + "多余内容".repeat(50);
        String cleaned = AiOutputCleaner.cleanQuestion(longQuestion, 100);
        assertEquals(true, cleaned.length() <= 100);
        assertEquals(true, cleaned.endsWith("。"));
        assertEquals(false, cleaned.contains("多余内容"));
    }

    @Test
    void returnsEmptyForNullOrBlankOutput() {
        assertEquals("", AiOutputCleaner.cleanQuestion(null, MAX));
        assertEquals("", AiOutputCleaner.cleanQuestion("   \n  ", MAX));
    }

    @Test
    void extractsFirstBalancedObjectIgnoringTrailingProse() {
        String raw = "结果如下：{\"totalScore\":88,\"note\":\"通过\"}\n以上评分供参考（备注 { 见附录）";
        assertEquals("{\"totalScore\":88,\"note\":\"通过\"}", AiOutputCleaner.extractJsonObject(raw));
    }

    @Test
    void ignoresBracesInsideStringLiterals() {
        String raw = "{\"a\":\"含 } 和 { 的字符串\",\"b\":2}";
        assertEquals(raw, AiOutputCleaner.extractJsonObject(raw));
    }

    @Test
    void extractsFirstArrayAndHandlesEscapedQuotes() {
        String raw = "```json\n[{\"title\":\"他说\\\"好\\\"\"},{\"title\":\"第二条\"}]\n```补充说明 [略]";
        assertEquals("[{\"title\":\"他说\\\"好\\\"\"},{\"title\":\"第二条\"}]",
                AiOutputCleaner.extractJsonArray(raw));
    }

    @Test
    void returnsNullWhenNoBalancedStructureExists() {
        assertNull(AiOutputCleaner.extractJsonObject("抱歉，我无法输出 JSON。"));
        assertNull(AiOutputCleaner.extractJsonObject("{\"unclosed\":1"));
        assertNull(AiOutputCleaner.extractJsonArray("没有数组"));
    }

    @Test
    void summarizeCollapsesNewlinesAndBoundsLength() {
        assertEquals("一行 到底", AiOutputCleaner.summarize("一行\n到底\n", 50));
        String summary = AiOutputCleaner.summarize("x".repeat(600), 100);
        assertEquals(101, summary.length());
        assertEquals(true, summary.endsWith("…"));
    }

    @Test
    void truncateKeepsInnerNewlinesForMultiLineBodies() {
        String body = "第一行\n第二行";
        assertEquals(body, AiOutputCleaner.truncate(body, 50));
        assertEquals("第一\n…", AiOutputCleaner.truncate("第一\n很长很长的正文内容", 3));
    }

    @Test
    void cleanQuestionNeverKeepsControlWhitespaceRuns() {
        String cleaned = AiOutputCleaner.cleanQuestion("请\t说明\n\n锁升级\n过程？", MAX);
        assertTrue(cleaned.matches("[^\\r\\n\\t]+"), "清洗后不应残留制表/换行：" + cleaned);
    }
}
