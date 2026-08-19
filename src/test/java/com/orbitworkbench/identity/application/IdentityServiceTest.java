package com.orbitworkbench.identity.application;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.orbitworkbench.identity.api.IdentityDtos.PasswordChangeRequest;
import com.orbitworkbench.identity.api.IdentityDtos.SetupRequest;
import com.orbitworkbench.identity.api.IdentityDtos.SetupResponse;
import com.orbitworkbench.identity.domain.AppUserRecord;
import com.orbitworkbench.identity.infrastructure.mapper.AppUserMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.workspace.application.WorkspaceService;
import com.orbitworkbench.workspace.domain.WorkspaceRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

@ExtendWith(MockitoExtension.class)
class IdentityServiceTest {

    @Mock
    private AppUserMapper userMapper;
    @Mock
    private WorkspaceService workspaceService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private FindByIndexNameSessionRepository<Session> sessionRepository;

    private IdentityService service;

    @BeforeEach
    void setUp() {
        service = new IdentityService(
                userMapper, workspaceService, passwordEncoder, sessionRepository);
    }

    @Test
    void setupCreatesUserAndDefaultWorkspace() {
        when(userMapper.findFirstForUpdate()).thenReturn(null);
        when(passwordEncoder.encode("secret")).thenReturn("bcrypt");
        when(userMapper.insert(any(AppUserRecord.class))).thenAnswer(invocation -> {
            AppUserRecord user = invocation.getArgument(0);
            user.setId(1L);
            return 1;
        });
        WorkspaceRecord workspace = new WorkspaceRecord();
        workspace.setId(2L);
        when(workspaceService.createDefault()).thenReturn(workspace);

        SetupResponse response = service.setup(new SetupRequest(" admin ", "secret"));

        assertAll(
                () -> assertEquals(1L, response.userId()),
                () -> assertEquals("admin", response.username()),
                () -> assertEquals(2L, response.defaultWorkspaceId()));
    }

    @Test
    void setupMapsSingletonConstraintConflictToStateConflict() {
        when(userMapper.findFirstForUpdate()).thenReturn(null);
        when(passwordEncoder.encode("secret")).thenReturn("bcrypt");
        when(userMapper.insert(any(AppUserRecord.class)))
                .thenThrow(new DuplicateKeyException("uk_app_user_singleton"));

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.setup(new SetupRequest("admin", "secret")));

        assertAll(
                () -> assertEquals(HttpStatus.CONFLICT, exception.getStatus()),
                () -> assertEquals(ErrorCode.STATE_CONFLICT, exception.getErrorCode()));
    }

    @Test
    void concurrentPasswordChangeFailsWhenOldHashCasMisses() {
        AppUserRecord user = new AppUserRecord();
        user.setId(1L);
        user.setUsername("admin");
        user.setPasswordHash("old-hash");
        user.setEnabled(true);
        when(userMapper.findById(1L)).thenReturn(user);
        when(passwordEncoder.matches("current", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("new-secret")).thenReturn("new-hash");
        when(userMapper.updatePassword(1L, "new-hash", "old-hash")).thenReturn(0);

        ApiException exception = assertThrows(ApiException.class, () ->
                service.changePassword(
                        1L, new PasswordChangeRequest("current", "new-secret")));

        assertAll(
                () -> assertEquals(HttpStatus.CONFLICT, exception.getStatus()),
                () -> assertEquals(ErrorCode.STATE_CONFLICT, exception.getErrorCode()));
        verifyNoInteractions(sessionRepository);
    }
}
