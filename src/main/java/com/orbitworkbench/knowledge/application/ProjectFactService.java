package com.orbitworkbench.knowledge.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbitworkbench.ai.application.AiOutputCleaner;
import com.orbitworkbench.ai.application.PromptCatalog;
import com.orbitworkbench.aiconnection.application.AiScenarioExecutionService;
import com.orbitworkbench.aiconnection.domain.AiScenario;
import com.orbitworkbench.knowledge.api.KnowledgeDtos.ConfirmFactRequest;
import com.orbitworkbench.knowledge.api.KnowledgeDtos.FactResponse;
import com.orbitworkbench.knowledge.api.KnowledgeDtos.FactsListResponse;
import com.orbitworkbench.knowledge.domain.FactSource;
import com.orbitworkbench.knowledge.domain.FactStatus;
import com.orbitworkbench.knowledge.domain.ProjectFactRecord;
import com.orbitworkbench.knowledge.infrastructure.mapper.ProjectFactMapper;
import com.orbitworkbench.project.domain.ProjectFileRecord;
import com.orbitworkbench.project.domain.ProjectRecord;
import com.orbitworkbench.project.infrastructure.mapper.ProjectMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.storage.application.LocalStorageService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 项目画像事实：AI 生成 ANALYZED 层（可被重新生成覆盖），用户确认后进入
 * USER_CONFIRMED/CONFIRMED 层且不会被再次生成覆盖（PRD §4.1 事实分层）。
 */
@Service
public class ProjectFactService {

    private static final Duration MODEL_TIMEOUT = Duration.ofSeconds(180);
    private static final int MAX_OUTPUT_TOKENS = 2048;
    private static final int MAX_FILES = 20;
    private static final int FILE_SNIPPET = 1500;
    private static final int MAX_FACTS = 30;
    private static final int MAX_TITLE = 255;
    private static final int MAX_CONTENT = 4000;
    private static final Set<String> FACT_TYPES = Set.of(
            "BUSINESS", "STRUCTURE", "RISK", "RESPONSIBILITY", "TECH_STACK", "OTHER");

    /** 正文见 resources/prompts/project-fact-system.txt。 */
    private static final String SYSTEM_PROMPT = PromptCatalog.load("project-fact-system");

    private final ProjectMapper projectMapper;
    private final ProjectFactMapper factMapper;
    private final LocalStorageService storageService;
    private final AiScenarioExecutionService aiScenarioExecution;
    private final ObjectMapper objectMapper;

    public ProjectFactService(ProjectMapper projectMapper,
                              ProjectFactMapper factMapper,
                              LocalStorageService storageService,
                              AiScenarioExecutionService aiScenarioExecution,
                              ObjectMapper objectMapper) {
        this.projectMapper = projectMapper;
        this.factMapper = factMapper;
        this.storageService = storageService;
        this.aiScenarioExecution = aiScenarioExecution;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public FactsListResponse generate(Long userId, Long projectId, Long versionId, Long connectionId) {
        requireVersion(userId, projectId, versionId);

        String material = buildMaterial(userId, versionId);
        if (material.isBlank()) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "该版本没有可分析的已解析文本文件");
        }
        String output = aiScenarioExecution.executeText(AiScenario.PROJECT_FACT, userId,
                connectionId, SYSTEM_PROMPT, factUserPrompt(material), MAX_OUTPUT_TOKENS,
                MODEL_TIMEOUT);
        List<ProjectFactRecord> facts = parseFacts(userId, versionId, output);

        factMapper.deleteAnalyzedByVersion(userId, versionId);
        facts.forEach(factMapper::insert);
        return list(userId, versionId);
    }

    @Transactional(readOnly = true)
    public FactsListResponse list(Long userId, Long versionId) {
        return new FactsListResponse(factMapper.listByVersion(userId, versionId).stream()
                .map(FactResponse::from).toList());
    }

    @Transactional
    public FactResponse confirm(Long userId, Long projectId, Long versionId, Long factId,
                                ConfirmFactRequest request) {
        requireVersion(userId, projectId, versionId);
        ProjectFactRecord record = factMapper.findByIdAndUser(factId, userId);
        if (record == null || !record.getProjectVersionId().equals(versionId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "画像事实不存在");
        }
        Instant now = Instant.now();
        if (factMapper.confirm(factId, userId, request.factType(), request.title().trim(),
                request.content().trim(), now, now) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT, "事实状态已变化");
        }
        return FactResponse.from(factMapper.findByIdAndUser(factId, userId));
    }

    @Transactional
    public FactResponse archive(Long userId, Long projectId, Long versionId, Long factId) {
        requireVersion(userId, projectId, versionId);
        if (factMapper.archive(factId, userId, Instant.now()) != 1) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "画像事实不存在");
        }
        return FactResponse.from(factMapper.findByIdAndUser(factId, userId));
    }

    private String buildMaterial(Long userId, Long versionId) {
        StringBuilder material = new StringBuilder();
        List<ProjectFileRecord> files = projectMapper.findFiles(versionId).stream()
                .filter(file -> "PARSED".equals(file.getStatus()))
                .filter(file -> file.getStorageRef() != null && !file.getStorageRef().isBlank())
                .limit(MAX_FILES)
                .toList();
        for (ProjectFileRecord file : files) {
            String text;
            try {
                text = storageService.readUtf8(file.getStorageRef());
            } catch (RuntimeException exception) {
                continue;
            }
            if (text == null || text.isBlank()) {
                continue;
            }
            material.append("### ").append(file.getRelativePath()).append('\n')
                    .append(text, 0, Math.min(text.length(), FILE_SNIPPET)).append("\n\n");
            if (material.length() > 24000) {
                break;
            }
        }
        return material.toString();
    }

    List<ProjectFactRecord> parseFacts(Long userId, Long versionId, String output) {
        String json = AiOutputCleaner.extractJsonArray(output);
        if (json == null) {
            throw unstructured("画像输出中没有 JSON 数组");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (Exception exception) {
            throw unstructured("画像输出不是合法 JSON 数组");
        }
        if (!root.isArray() || root.isEmpty()) {
            throw unstructured("画像输出为空数组");
        }
        if (root.size() > MAX_FACTS) {
            throw unstructured("画像事实数量超过 " + MAX_FACTS + " 条");
        }
        List<ProjectFactRecord> facts = new ArrayList<>();
        Instant now = Instant.now();
        int index = 0;
        for (JsonNode node : root) {
            index += 1;
            String factType = node.path("factType").asText("");
            String title = AiOutputCleaner.summarize(node.path("title").asText(""), MAX_TITLE);
            String content = AiOutputCleaner.truncate(node.path("content").asText("").trim(),
                    MAX_CONTENT);
            int confidence = node.path("confidence").isNumber() ? node.path("confidence").asInt() : -1;
            if (!FACT_TYPES.contains(factType) || title.isBlank() || content.isBlank()
                    || confidence < 0 || confidence > 100) {
                // 只报序号：模型产出的字段内容不得进入错误摘要、日志与接口响应
                throw unstructured("第 " + index + " 条画像事实字段非法");
            }
            ProjectFactRecord record = new ProjectFactRecord();
            record.setUserId(userId);
            record.setProjectVersionId(versionId);
            record.setFactType(factType);
            record.setTitle(title);
            record.setContent(content);
            record.setSource(FactSource.AI_ANALYZED);
            record.setConfirmationStatus(FactStatus.ANALYZED);
            record.setConfidence(confidence);
            record.setCreatedAt(now);
            record.setUpdatedAt(now);
            facts.add(record);
        }
        return facts;
    }

    private ApiException unstructured(String message) {
        return new ApiException(HttpStatus.BAD_GATEWAY, ErrorCode.INVALID_STRUCTURED_OUTPUT, message);
    }

    private String factUserPrompt(String material) {
        return "项目文件片段：\n" + material + "\n请按约定输出画像事实 JSON 数组。";
    }

    private void requireVersion(Long userId, Long projectId, Long versionId) {
        ProjectRecord project = projectMapper.findProjectByIdAndUserId(projectId, userId);
        if (project == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "项目不存在");
        }
        boolean found = projectMapper.findVersions(projectId).stream()
                .anyMatch(version -> version.getId().equals(versionId));
        if (!found) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "项目版本不存在");
        }
    }
}
