package com.orbitworkbench.knowledge.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private static final Set<String> FACT_TYPES = Set.of(
            "BUSINESS", "STRUCTURE", "RISK", "RESPONSIBILITY", "TECH_STACK", "OTHER");

    private static final String SYSTEM_PROMPT = """
            你是项目画像分析师。根据给定的项目文件片段，提取 3 到 8 条画像事实，覆盖：
            业务背景(BUSINESS)、结构(STRUCTURE)、风险点(RISK)、用户可能负责的部分(RESPONSIBILITY)、
            技术栈(TECH_STACK)。每条事实给出 confidence 0-100（对该判断的把握）。
            只输出一个 JSON 数组，不要其他文字，元素结构：
            {"factType":"BUSINESS|STRUCTURE|RISK|RESPONSIBILITY|TECH_STACK|OTHER",
             "title":"不超过 40 字的标题","content":"不超过 300 字的事实描述","confidence":0-100}
            系统推断的事实不得表述为用户亲自负责；RESONSIBILITY 类需写“疑似/可能”。""";

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

    private List<ProjectFactRecord> parseFacts(Long userId, Long versionId, String output) {
        String json = extractArray(output);
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (Exception exception) {
            throw unstructured("画像输出不是合法 JSON 数组");
        }
        if (!root.isArray() || root.isEmpty()) {
            throw unstructured("画像输出为空数组");
        }
        List<ProjectFactRecord> facts = new ArrayList<>();
        Instant now = Instant.now();
        root.forEach(node -> {
            String factType = node.path("factType").asText("");
            String title = node.path("title").asText("");
            String content = node.path("content").asText("");
            int confidence = node.path("confidence").asInt(-1);
            if (!FACT_TYPES.contains(factType) || title.isBlank() || content.isBlank()
                    || confidence < 0 || confidence > 100) {
                throw unstructured("画像事实字段非法：" + title);
            }
            ProjectFactRecord record = new ProjectFactRecord();
            record.setUserId(userId);
            record.setProjectVersionId(versionId);
            record.setFactType(factType);
            record.setTitle(title.trim());
            record.setContent(content.trim());
            record.setSource(FactSource.AI_ANALYZED);
            record.setConfirmationStatus(FactStatus.ANALYZED);
            record.setConfidence(confidence);
            record.setCreatedAt(now);
            record.setUpdatedAt(now);
            facts.add(record);
        });
        return facts;
    }

    private String extractArray(String output) {
        int start = output.indexOf('[');
        int end = output.lastIndexOf(']');
        if (start < 0 || end <= start) {
            throw unstructured("画像输出中没有 JSON 数组");
        }
        return output.substring(start, end + 1);
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
