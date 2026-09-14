package com.orbitworkbench.knowledge.application;

import com.orbitworkbench.aiconnection.application.AiScenarioExecutionService;
import com.orbitworkbench.aiconnection.domain.AiScenario;
import com.orbitworkbench.ai.application.PromptCatalog;
import com.orbitworkbench.knowledge.api.KnowledgeDtos.AskResponse;
import com.orbitworkbench.knowledge.api.KnowledgeDtos.BuildResultResponse;
import com.orbitworkbench.knowledge.api.KnowledgeDtos.SourceItem;
import com.orbitworkbench.knowledge.domain.KnowledgeChunkRecord;
import com.orbitworkbench.knowledge.infrastructure.mapper.KnowledgeChunkMapper;
import com.orbitworkbench.project.domain.ProjectFileRecord;
import com.orbitworkbench.project.domain.ProjectRecord;
import com.orbitworkbench.project.domain.ProjectVersionRecord;
import com.orbitworkbench.project.infrastructure.mapper.ProjectMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.storage.application.LocalStorageService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 知识块构建与检索问答（RAG 第一版：MySQL ngram 全文检索 + AI 带来源回答）。
 * 切分为确定性文本处理（1200 字窗口、150 字重叠），重建幂等（先删该版本旧块）。
 * 问答先检索资料块，检索不到直接返回“资料不足”，不编造答案。
 */
@Service
public class KnowledgeService {

    private static final int CHUNK_WINDOW = 1200;
    private static final int CHUNK_OVERLAP = 150;
    private static final int MAX_FILE_CHARS = 60000;
    private static final int MAX_CHUNK_FILES = 200;
    private static final int SEARCH_LIMIT = 6;
    private static final int ANSWER_MAX_TOKENS = 2048;
    private static final Duration ANSWER_TIMEOUT = Duration.ofSeconds(120);
    private static final int SOURCE_SNIPPET = 300;
    /** 正文见 resources/prompts/knowledge-answer-system.txt。 */
    private static final String ANSWER_SYSTEM_PROMPT = PromptCatalog.load("knowledge-answer-system");
    /** 正文见 resources/prompts/personal-answer-system.txt（V48「我的状态」范围）。 */
    private static final String PERSONAL_SYSTEM_PROMPT = PromptCatalog.load("personal-answer-system");

    private final ProjectMapper projectMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final LocalStorageService storageService;
    private final AiScenarioExecutionService aiScenarioExecution;
    private final com.orbitworkbench.userfact.application.UserFactService userFactService;
    private final PersonalContextService personalContextService;

    public KnowledgeService(ProjectMapper projectMapper,
                            KnowledgeChunkMapper chunkMapper,
                            LocalStorageService storageService,
                            AiScenarioExecutionService aiScenarioExecution,
                            com.orbitworkbench.userfact.application.UserFactService userFactService,
                            PersonalContextService personalContextService) {
        this.projectMapper = projectMapper;
        this.chunkMapper = chunkMapper;
        this.storageService = storageService;
        this.aiScenarioExecution = aiScenarioExecution;
        this.userFactService = userFactService;
        this.personalContextService = personalContextService;
    }

    /** 个人记忆层注入（V45 溯源）：只带用户确认过的画像事实/编译快照，NONE 表示本次不注入。 */
    private com.orbitworkbench.ai.application.MemoryContext memoryContext(Long userId) {
        return userFactService.confirmedContext(userId);
    }

    private static String memoryBlock(com.orbitworkbench.ai.application.MemoryContext memory) {
        return memory.present() ? memory.text() + '\n' : "";
    }

    @Transactional
    public BuildResultResponse build(Long userId, Long projectId, Long versionId) {
        requireVersion(userId, projectId, versionId);
        List<ProjectFileRecord> files = projectMapper.findFiles(versionId).stream()
                .filter(file -> "PARSED".equals(file.getStatus()))
                .filter(file -> file.getStorageRef() != null && !file.getStorageRef().isBlank())
                .limit(MAX_CHUNK_FILES)
                .toList();

        chunkMapper.deleteByVersion(userId, versionId);
        Instant now = Instant.now();
        int chunkCount = 0;
        int fileCount = 0;
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
            if (text.length() > MAX_FILE_CHARS) {
                text = text.substring(0, MAX_FILE_CHARS);
            }
            for (String piece : split(text)) {
                KnowledgeChunkRecord chunk = new KnowledgeChunkRecord();
                chunk.setUserId(userId);
                chunk.setProjectVersionId(versionId);
                chunk.setProjectFileId(file.getId());
                chunk.setRelativePath(file.getRelativePath());
                chunk.setChunkNo(chunkCount + 1);
                chunk.setContent(piece);
                chunk.setCreatedAt(now);
                chunkMapper.insert(chunk);
                chunkCount += 1;
            }
            fileCount += 1;
        }
        return new BuildResultResponse(chunkCount, fileCount);
    }

    public AskResponse ask(Long userId, String question, Long projectVersionId) {
        return ask(userId, question, projectVersionId, "MATERIALS");
    }

    /**
     * 问答（V48 双范围）：MATERIALS 检索项目知识块；PERSONAL 聚合个人数据核心六源。
     * 两者都不编造：项目范围无命中走「资料不足」，个人范围无数据同样如实说明。
     */
    public AskResponse ask(Long userId, String question, Long projectVersionId, String scope) {
        String keyword = question.trim();
        if ("PERSONAL".equals(scope)) {
            PersonalContextService.PersonalContext personal = personalContextService.build(userId, keyword);
            if (personal.isEmpty()) {
                return AskResponse.empty();
            }
            com.orbitworkbench.ai.application.MemoryContext memory = memoryContext(userId);
            String answer = aiScenarioExecution.executeText(AiScenario.KNOWLEDGE_ANSWER, userId, null,
                    PERSONAL_SYSTEM_PROMPT,
                    memoryBlock(memory) + "资料：\n" + personal.context() + "\n问题：" + keyword,
                    ANSWER_MAX_TOKENS, ANSWER_TIMEOUT,
                    com.orbitworkbench.ai.application.WebSearchMode.DISABLED, memory).trim();
            return new AskResponse(answer, false, personal.sources());
        }
        List<KnowledgeChunkRecord> chunks = searchSafely(userId, projectVersionId, keyword);
        if (chunks.isEmpty()) {
            return AskResponse.empty();
        }

        List<SourceItem> sources = new ArrayList<>();
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < chunks.size(); i += 1) {
            KnowledgeChunkRecord chunk = chunks.get(i);
            String snippet = snippet(chunk.getContent(), SOURCE_SNIPPET);
            sources.add(new SourceItem(chunk.getRelativePath(), chunk.getChunkNo(), snippet));
            context.append('[').append(i + 1).append("] ").append(chunk.getRelativePath())
                    .append(" 第").append(chunk.getChunkNo()).append("段\n")
                    .append(snippet(chunk.getContent(), 1200)).append("\n\n");
        }

        com.orbitworkbench.ai.application.MemoryContext memory = memoryContext(userId);
        String answer = aiScenarioExecution.executeText(AiScenario.KNOWLEDGE_ANSWER, userId, null,
                ANSWER_SYSTEM_PROMPT, memoryBlock(memory) + "资料：\n" + context + "\n问题：" + keyword,
                ANSWER_MAX_TOKENS, ANSWER_TIMEOUT,
                com.orbitworkbench.ai.application.WebSearchMode.DISABLED, memory).trim();
        return new AskResponse(answer, false, sources);
    }

    /** 流式问答的准备结果：命中的来源、拼好的上下文与记忆溯源（V45）。空命中时 sources 为空、insufficient 为 true。 */
    public record AskStreamPreparation(List<SourceItem> sources, String userPrompt, boolean insufficient,
                                       com.orbitworkbench.ai.application.MemoryContext memory) {}

    /**
     * 流式问答第一步：检索与上下文准备（同步、快）。命中为空直接走「资料不足」，
     * 不开流；命中则返回准备结果，由 Controller 开流透传增量。
     */
    public AskStreamPreparation prepareAsk(Long userId, String question, Long projectVersionId) {
        return prepareAsk(userId, question, projectVersionId, "MATERIALS");
    }

    public AskStreamPreparation prepareAsk(Long userId, String question, Long projectVersionId,
                                           String scope) {
        String keyword = question.trim();
        if ("PERSONAL".equals(scope)) {
            PersonalContextService.PersonalContext personal = personalContextService.build(userId, keyword);
            if (personal.isEmpty()) {
                return new AskStreamPreparation(List.of(), "", true,
                        com.orbitworkbench.ai.application.MemoryContext.NONE);
            }
            com.orbitworkbench.ai.application.MemoryContext memory = memoryContext(userId);
            return new AskStreamPreparation(personal.sources(),
                    memoryBlock(memory) + "资料：\n" + personal.context() + "\n问题：" + keyword,
                    false, memory);
        }
        List<KnowledgeChunkRecord> chunks = searchSafely(userId, projectVersionId, keyword);
        if (chunks.isEmpty()) {
            return new AskStreamPreparation(List.of(), "", true,
                    com.orbitworkbench.ai.application.MemoryContext.NONE);
        }
        List<SourceItem> sources = new ArrayList<>();
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < chunks.size(); i += 1) {
            KnowledgeChunkRecord chunk = chunks.get(i);
            String snippet = snippet(chunk.getContent(), SOURCE_SNIPPET);
            sources.add(new SourceItem(chunk.getRelativePath(), chunk.getChunkNo(), snippet));
            context.append('[').append(i + 1).append("] ").append(chunk.getRelativePath())
                    .append(" 第").append(chunk.getChunkNo()).append("段\n")
                    .append(snippet(chunk.getContent(), 1200)).append("\n\n");
        }
        com.orbitworkbench.ai.application.MemoryContext memory = memoryContext(userId);
        return new AskStreamPreparation(sources,
                memoryBlock(memory) + "资料：\n" + context + "\n问题：" + keyword, false, memory);
    }

    /** 流式问答使用的系统提示词（Controller 开流用）；按范围切换。 */
    public String answerSystemPrompt() {
        return ANSWER_SYSTEM_PROMPT;
    }

    public String answerSystemPrompt(String scope) {
        return "PERSONAL".equals(scope) ? PERSONAL_SYSTEM_PROMPT : ANSWER_SYSTEM_PROMPT;
    }

    public int answerMaxTokens() {
        return ANSWER_MAX_TOKENS;
    }

    public java.time.Duration answerTimeout() {
        return ANSWER_TIMEOUT;
    }

    public List<KnowledgeChunkRecord> searchSafely(Long userId, Long projectVersionId, String keyword) {
        List<KnowledgeChunkRecord> chunks =
                chunkMapper.search(userId, projectVersionId, keyword, SEARCH_LIMIT);
        if (chunks.isEmpty()) {
            chunks = chunkMapper.searchFallbackLike(userId, projectVersionId, keyword, SEARCH_LIMIT);
        }
        return chunks;
    }

    private List<String> split(String text) {
        List<String> pieces = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + CHUNK_WINDOW);
            pieces.add(text.substring(start, end));
            if (end >= text.length()) {
                break;
            }
            start = end - CHUNK_OVERLAP;
        }
        return pieces;
    }

    private String snippet(String value, int limit) {
        String flat = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        return flat.length() <= limit ? flat : flat.substring(0, limit) + "…";
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
