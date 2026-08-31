package com.orbitworkbench.knowledge.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.knowledge.domain.ProjectFactRecord;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

    @Test
    void rejectsConfidenceOutsideZeroToHundred() {
        ApiException tooHigh = assertThrows(ApiException.class, () -> service.parseFacts(7L, 41L,
                "[{\"factType\":\"RISK\",\"title\":\"风险\",\"content\":\"正文\",\"confidence\":101}]"));
        assertTrue(tooHigh.getMessage().contains("第 1 条"), tooHigh.getMessage());

        ApiException textValue = assertThrows(ApiException.class, () -> service.parseFacts(7L, 41L,
                "[{\"factType\":\"RISK\",\"title\":\"风险\",\"content\":\"正文\",\"confidence\":\"高\"}]"));
        assertTrue(textValue.getMessage().contains("第 1 条"), textValue.getMessage());

        List<ProjectFactRecord> accepted = service.parseFacts(7L, 41L,
                "[{\"factType\":\"RISK\",\"title\":\"风险\",\"content\":\"正文\",\"confidence\":0},"
                        + "{\"factType\":\"RISK\",\"title\":\"风险\",\"content\":\"正文\",\"confidence\":100}]");
        assertEquals(2, accepted.size());
    }

    @Test
    void reportsIllegalFactTypeByPositionOnly() {
        String output = "[{\"factType\":\"BUSINESS\",\"title\":\"ok\",\"content\":\"正文\",\"confidence\":50},"
                + "{\"factType\":\"GUESS_ME\",\"title\":\"bad\",\"content\":\"正文\",\"confidence\":50}]";

        ApiException exception = assertThrows(ApiException.class,
                () -> service.parseFacts(7L, 41L, output));

        assertEquals("第 2 条画像事实字段非法", exception.getMessage());
    }

    @Test
    void generateRejectsVersionWithoutParsedMaterial() {
        mockVersion(31L, 41L);
        when(projectMapper.findFiles(41L)).thenReturn(List.of());

        ApiException exception = assertThrows(ApiException.class,
                () -> service.generate(7L, 31L, 41L, null));

        assertEquals(org.springframework.http.HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("该版本没有可分析的已解析文本文件", exception.getMessage());
        verify(execution, never()).executeText(any(), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.anyInt(), any());
    }

    @Test
    void generateFeedsAtMostTwentyFilesIntoThePrompt() {
        mockVersion(31L, 41L);
        List<com.orbitworkbench.project.domain.ProjectFileRecord> files = new java.util.ArrayList<>();
        for (int index = 0; index < 25; index += 1) {
            files.add(parsedFile("docs/说明" + index + ".md", "ref-" + index));
        }
        files.add(skippedFile("docs/未解析.md"));
        when(projectMapper.findFiles(41L)).thenReturn(files);
        when(storageService.readUtf8(org.mockito.ArgumentMatchers.startsWith("ref-")))
                .thenReturn("秒杀系统峰值 10000 QPS");
        when(execution.executeText(any(), any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.anyInt(), any()))
                .thenReturn("[{\"factType\":\"BUSINESS\",\"title\":\"秒杀\",\"content\":\"正文\",\"confidence\":60}]");
        when(factMapper.listByVersion(7L, 41L)).thenReturn(List.of());

        service.generate(7L, 31L, 41L, null);

        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(execution).executeText(any(), any(), any(), any(), prompt.capture(),
                org.mockito.ArgumentMatchers.anyInt(), any());
        long sections = java.util.stream.IntStream.range(0, prompt.getValue().length())
                .filter(i -> prompt.getValue().startsWith("### docs/", i)).count();
        assertEquals(20, sections, "素材应截到最多 20 个文件");
        assertTrue(prompt.getValue().contains("说明19"), prompt.getValue());
        assertTrue(!prompt.getValue().contains("说明20"), "第 21 个文件不得进入提示词");
    }

    @Test
    void confirmRejectsConcurrentFactChange() {
        mockVersion(31L, 41L);
        when(factMapper.findByIdAndUser(81L, 7L)).thenReturn(fact(81L, 41L));
        when(factMapper.confirm(org.mockito.ArgumentMatchers.eq(81L),
                org.mockito.ArgumentMatchers.eq(7L), any(), any(), any(), any(), any()))
                .thenReturn(0);

        ApiException exception = assertThrows(ApiException.class, () -> service.confirm(7L, 31L, 41L,
                81L, new com.orbitworkbench.knowledge.api.KnowledgeDtos.ConfirmFactRequest(
                        "BUSINESS", "标题", "正文")));

        assertEquals(org.springframework.http.HttpStatus.CONFLICT, exception.getStatus());
        assertEquals("事实状态已变化", exception.getMessage());
    }

    @Test
    void confirmRejectsFactOfAnotherVersion() {
        mockVersion(31L, 41L);
        when(factMapper.findByIdAndUser(81L, 7L)).thenReturn(fact(81L, 99L));

        ApiException exception = assertThrows(ApiException.class, () -> service.confirm(7L, 31L, 41L,
                81L, new com.orbitworkbench.knowledge.api.KnowledgeDtos.ConfirmFactRequest(
                        "BUSINESS", "标题", "正文")));

        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, exception.getStatus());
    }

    @Test
    void archiveRejectsMissingFact() {
        mockVersion(31L, 41L);
        when(factMapper.archive(org.mockito.ArgumentMatchers.eq(81L),
                org.mockito.ArgumentMatchers.eq(7L), any())).thenReturn(0);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.archive(7L, 31L, 41L, 81L));

        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals("画像事实不存在", exception.getMessage());
    }

    private void mockVersion(Long projectId, Long versionId) {
        com.orbitworkbench.project.domain.ProjectRecord project =
                new com.orbitworkbench.project.domain.ProjectRecord();
        project.setId(projectId);
        when(projectMapper.findProjectByIdAndUserId(projectId, 7L)).thenReturn(project);
        com.orbitworkbench.project.domain.ProjectVersionRecord version =
                new com.orbitworkbench.project.domain.ProjectVersionRecord();
        version.setId(versionId);
        version.setProjectId(projectId);
        when(projectMapper.findVersions(projectId)).thenReturn(List.of(version));
    }

    private com.orbitworkbench.project.domain.ProjectFileRecord parsedFile(String path, String ref) {
        com.orbitworkbench.project.domain.ProjectFileRecord record =
                new com.orbitworkbench.project.domain.ProjectFileRecord();
        record.setRelativePath(path);
        record.setStorageRef(ref);
        record.setStatus("PARSED");
        return record;
    }

    private com.orbitworkbench.project.domain.ProjectFileRecord skippedFile(String path) {
        com.orbitworkbench.project.domain.ProjectFileRecord record =
                new com.orbitworkbench.project.domain.ProjectFileRecord();
        record.setRelativePath(path);
        record.setStorageRef("ref-skipped");
        record.setStatus("FAILED");
        return record;
    }

    private com.orbitworkbench.knowledge.domain.ProjectFactRecord fact(Long id, Long versionId) {
        com.orbitworkbench.knowledge.domain.ProjectFactRecord record =
                new com.orbitworkbench.knowledge.domain.ProjectFactRecord();
        record.setId(id);
        record.setProjectVersionId(versionId);
        record.setUserId(7L);
        record.setFactType("BUSINESS");
        record.setTitle("标题");
        record.setContent("正文");
        return record;
    }
}
