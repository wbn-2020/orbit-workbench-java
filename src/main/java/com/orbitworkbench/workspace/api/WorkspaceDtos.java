package com.orbitworkbench.workspace.api;

import com.orbitworkbench.workspace.domain.WorkspaceRecord;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class WorkspaceDtos {
    private WorkspaceDtos() {}

    public record WorkspaceRequest(@NotBlank @Size(max = 128) String name,
                                   @Size(max = 512) String description) {}

    public record WorkspaceResponse(Long id, String name, String description,
                                    boolean isDefault, String status,
                                    Instant createdAt, Instant updatedAt) {
        public static WorkspaceResponse from(WorkspaceRecord w) {
            return new WorkspaceResponse(w.getId(), w.getName(), w.getDescription(),
                    w.isDefaultWorkspace(), w.getStatus(),
                    w.getCreatedAt(), w.getUpdatedAt());
        }
    }
}
