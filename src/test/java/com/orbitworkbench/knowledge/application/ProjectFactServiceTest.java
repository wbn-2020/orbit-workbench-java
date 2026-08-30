package com.orbitworkbench.knowledge.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.knowledge.domain.ProjectFactRecord;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjectFactServiceTest {

    @Mock
    private com.orbitworkbench.project.infrastructure.mapper.ProjectMapper projectMapper;

    @Mock
    private com.orbitworkbench.knowledge.infrastructure.mapper.ProjectFactMapper factMapper;

    @Mock
    private com.orbitworkbench.storage.application.LocalStorageService storageService;

    @Mock
    private com.orbitworkbench.aiconnection.application.AiScenarioExecutionService execution;

    private ProjectFactService service;

    @BeforeEach
    void setUp() {
        service = new ProjectFactService(projectMapper, factMapper, storageService, execution,
                new ObjectMapper());
    }

    @Test
    void parsesFencedArrayAndIgnoresTrailingProse() {
        String output = "分析如下：\n```json\n"
                + "[{\"factType\":\"BUSINESS\",\"title\":\"秒杀场景\",\"content\":\"峰值 10000 QPS\",\"confidence\":80},"
                + "{\"factType\":\"TECH_STACK\",\"title\":\"技术栈\",\"content\":\"Spring Boot 3 + Redis\",\"confidence\":70}]"
                + "\n```\n以上为初步判断（详见 [附录]）。";

        List<ProjectFactRecord> facts = service.parseFacts(7L, 41L, output);

        assertEquals(2, facts.size());
        assertEquals("BUSINESS", facts.get(0).getFactType());
        assertEquals("秒杀场景", facts.get(0).getTitle());
        assertEquals("Spring Boot 3 + Redis", facts.get(1).getContent());
        assertEquals(70, facts.get(1).getConfidence());
    }

    @Test
    void invalidFactErrorNeverCarriesModelText() {
        String output = "[{\"factType\":\"BUSINESS\",\"title\":\"sk-live-secret-abcdef\","
                + "\"content\":\"正文\",\"confidence\":\"非常高\"}]";

        ApiException exception = assertThrows(ApiException.class,
                () -> service.parseFacts(7L, 41L, output));

        assertEquals(ErrorCode.INVALID_STRUCTURED_OUTPUT, exception.getErrorCode());
        assertTrue(!exception.getMessage().contains("sk-live-secret"),
                "错误消息不得带上模型原文，实际：" + exception.getMessage());
        assertTrue(exception.getMessage().contains("第 1 条"),
                "应改为按序号定位问题项，实际：" + exception.getMessage());
    }

    @Test
    void rejectsRunawayFactCount() {
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < 40; index += 1) {
            builder.append(index > 0 ? "," : "")
                    .append("{\"factType\":\"OTHER\",\"title\":\"事实").append(index)
                    .append("\",\"content\":\"内容\",\"confidence\":50}");
        }
        builder.append("]");

        ApiException exception = assertThrows(ApiException.class,
                () -> service.parseFacts(7L, 41L, builder.toString()));

        assertTrue(exception.getMessage().contains("30"),
                "应说明条数上限，实际：" + exception.getMessage());
    }

    @Test
    void boundsTitleAndContentToColumnWidths() {
        String output = "[{\"factType\":\"RISK\",\"title\":\"" + "标".repeat(600)
                + "\",\"content\":\"" + "文".repeat(5000) + "\",\"confidence\":60}]";

        List<ProjectFactRecord> facts = service.parseFacts(7L, 41L, output);

        assertTrue(facts.get(0).getTitle().length() <= 256,
                "标题应裁到列宽内，实际：" + facts.get(0).getTitle().length());
        assertTrue(facts.get(0).getContent().length() <= 4001,
                "正文应有上限，实际：" + facts.get(0).getContent().length());
    }

    @Test
    void missingArrayOrEmptyOutputFailsStructured() {
        ApiException absent = assertThrows(ApiException.class,
                () -> service.parseFacts(7L, 41L, "抱歉，我无法分析。"));
        assertTrue(absent.getMessage().contains("没有 JSON 数组"));

        ApiException empty = assertThrows(ApiException.class,
                () -> service.parseFacts(7L, 41L, "[]"));
        assertEquals(ErrorCode.INVALID_STRUCTURED_OUTPUT, empty.getErrorCode());
    }
}
