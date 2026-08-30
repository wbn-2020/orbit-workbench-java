package com.orbitworkbench.knowledge.application;

import com.orbitworkbench.knowledge.api.KnowledgeDtos.BuildResultResponse;
import com.orbitworkbench.notification.application.NotificationService;
import com.orbitworkbench.notification.domain.NotificationEvent;
import com.orbitworkbench.project.application.ProjectVersionImportedEvent;
import com.orbitworkbench.project.domain.ProjectRecord;
import com.orbitworkbench.project.domain.ProjectVersionRecord;
import com.orbitworkbench.project.infrastructure.mapper.ProjectMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 知识块构建编排：导入成功后异步自动构建，页面可同步重建（重试）。
 * 状态机落在 project_version.knowledge_build_status：
 * 自动构建仅从 PENDING 进入 BUILDING（幂等，重复触发直接跳过）；
 * 手动重建允许 PENDING/READY/FAILED 进入 BUILDING，BUILDING 时返回冲突。
 * 构建失败记录摘要化错误信息，不阻断导入本身。
 */
@Service
public class KnowledgeBuildService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBuildService.class);
    private static final int MAX_ERROR_LENGTH = 500;

    private final ProjectMapper projectMapper;
    private final KnowledgeService knowledgeService;
    private final NotificationService notificationService;

    public KnowledgeBuildService(ProjectMapper projectMapper, KnowledgeService knowledgeService,
                                 NotificationService notificationService) {
        this.projectMapper = projectMapper;
        this.knowledgeService = knowledgeService;
        this.notificationService = notificationService;
    }

    @Async
    public void buildAfterImport(ProjectVersionImportedEvent event) {
        Long versionId = event.versionId();
        int claimed = projectMapper.tryMarkKnowledgeBuilding(
                versionId, List.of("PENDING"), Instant.now());
        if (claimed != 1) {
            return;
        }
        try {
            BuildResultResponse result =
                    knowledgeService.build(event.userId(), event.projectId(), versionId);
            Instant now = Instant.now();
            projectMapper.markKnowledgeReady(versionId, result.chunkCount(), now, now);
        } catch (RuntimeException exception) {
            log.warn("知识块自动构建失败，版本 id={}：{}", versionId, exception.toString());
            String summary = sanitizedError(exception);
            projectMapper.markKnowledgeFailed(versionId, summary, Instant.now());
            notifyBuildFailed(event.userId(), event.projectId(), versionId, summary);
        }
    }

    public BuildResultResponse rebuild(Long userId, Long projectId, Long versionId) {
        requireVersion(userId, projectId, versionId);
        int claimed = projectMapper.tryMarkKnowledgeBuilding(
                versionId, List.of("PENDING", "READY", "FAILED"), Instant.now());
        if (claimed != 1) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "知识块正在构建中，请稍后再试");
        }
        try {
            BuildResultResponse result = knowledgeService.build(userId, projectId, versionId);
            Instant now = Instant.now();
            projectMapper.markKnowledgeReady(versionId, result.chunkCount(), now, now);
            return result;
        } catch (RuntimeException exception) {
            String summary = sanitizedError(exception);
            projectMapper.markKnowledgeFailed(versionId, summary, Instant.now());
            notifyBuildFailed(userId, projectId, versionId, summary);
            throw exception;
        }
    }

    private void notifyBuildFailed(Long userId, Long projectId, Long versionId, String summary) {
        notificationService.notify(
                NotificationEvent.KNOWLEDGE_BUILD_FAILED,
                userId,
                "项目知识块构建失败",
                "该版本知识块构建未完成：" + summary,
                NotificationService.RESOURCE_PROJECT_VERSION,
                versionId,
                "/projects/" + projectId,
                "KNOWLEDGE_BUILD_FAILED:" + versionId);
    }

    private void requireVersion(Long userId, Long projectId, Long versionId) {
        ProjectRecord project = projectMapper.findProjectByIdAndUserId(projectId, userId);
        if (project == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "项目不存在");
        }
        ProjectVersionRecord version = projectMapper.findVersionById(versionId);
        if (version == null || !projectId.equals(version.getProjectId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND, "项目版本不存在");
        }
    }

    private String sanitizedError(RuntimeException exception) {
        String message = exception.getMessage();
        String summary = exception.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
        return summary.length() <= MAX_ERROR_LENGTH ? summary : summary.substring(0, MAX_ERROR_LENGTH);
    }
}
