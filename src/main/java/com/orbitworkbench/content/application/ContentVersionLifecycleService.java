package com.orbitworkbench.content.application;

import com.orbitworkbench.content.infrastructure.mapper.ContentVersionMapper;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContentVersionLifecycleService {

    private final ContentVersionMapper versionMapper;

    public ContentVersionLifecycleService(ContentVersionMapper versionMapper) {
        this.versionMapper = versionMapper;
    }

    @Transactional
    public void markRunning(Long taskId, Long runId) {
        versionMapper.markRunning(taskId, runId, Instant.now());
    }

    @Transactional
    public void completeFromArtifact(Long taskId,
                                     Long runId,
                                     Long artifactId,
                                     Long artifactVersionId) {
        versionMapper.markSucceeded(
                taskId, runId, artifactId, artifactVersionId, Instant.now());
    }

    @Transactional
    public void fail(Long taskId,
                     String errorCode,
                     String errorSummary) {
        versionMapper.markFailed(
                taskId, "FAILED", errorCode, safeSummary(errorSummary), Instant.now());
    }

    @Transactional
    public void pause(Long taskId) {
        versionMapper.markFailed(
                taskId, "PAUSED", "PAUSED", "AgentRun 已暂停", Instant.now());
    }

    @Transactional
    public void cancel(Long taskId) {
        versionMapper.markFailed(
                taskId, "CANCELLED", "CANCELLED", "AgentRun 已取消", Instant.now());
    }

    private String safeSummary(String value) {
        if (value == null || value.isBlank()) {
            return "内容生成失败";
        }
        String normalized = value.replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .trim();
        return normalized.length() <= 512 ? normalized : normalized.substring(0, 512);
    }
}
