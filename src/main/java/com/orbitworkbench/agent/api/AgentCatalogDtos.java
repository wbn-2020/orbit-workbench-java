package com.orbitworkbench.agent.api;

import java.time.Instant;
import java.util.List;

public final class AgentCatalogDtos {

    private AgentCatalogDtos() {
    }

    public record AgentDefinitionResponse(
            Long id,
            String name,
            String description,
            String status,
            Long defaultConnectionId,
            Long defaultModelProfileId,
            Long promptVersionId,
            Long promptTemplateId,
            String promptTemplateName,
            Integer promptVersionNumber,
            String promptContent,
            List<String> promptVariables,
            java.util.Map<String, Object> configuration,
            String moduleType,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record PromptVersionResponse(
            Long id,
            Long templateId,
            String templateName,
            String purpose,
            Integer versionNumber,
            String content,
            List<String> variables,
            Instant createdAt
    ) {
    }
}
