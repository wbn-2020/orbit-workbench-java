package com.orbitworkbench.identity.api;

import com.orbitworkbench.identity.domain.AppUserRecord;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class IdentityDtos {
    private IdentityDtos() {}

    public record SetupRequest(@NotBlank @Size(min = 3, max = 32) String username,
                               @NotBlank @Size(min = 6, max = 128) String password) {}
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record PasswordChangeRequest(@NotBlank String currentPassword,
                                        @NotBlank @Size(min = 6, max = 128) String newPassword) {}
    public record UserResponse(Long id, String username) {
        public static UserResponse from(AppUserRecord user) {
            return new UserResponse(user.getId(), user.getUsername());
        }
    }
    public record SetupResponse(Long userId, String username, Long defaultWorkspaceId) {}
    public record SetupStatusResponse(boolean initialized) {}
    public record CsrfResponse(String headerName) {}
}
