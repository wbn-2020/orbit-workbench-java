package com.orbitworkbench.artifact.application;

public record CreateInitialArtifactCommand(
        Long workspaceId,
        Long taskId,
        Long sourceRunId,
        String artifactType,
        String title,
        String content,
        String contentFormat,
        String changeSummary
) {
}
