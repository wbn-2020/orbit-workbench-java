package com.orbitworkbench.identity.application;

import com.orbitworkbench.identity.api.IdentityDtos.PasswordChangeRequest;
import com.orbitworkbench.identity.api.IdentityDtos.SetupRequest;
import com.orbitworkbench.identity.api.IdentityDtos.SetupResponse;
import com.orbitworkbench.identity.api.IdentityDtos.UserResponse;
import com.orbitworkbench.identity.domain.AppUserRecord;
import com.orbitworkbench.identity.infrastructure.mapper.AppUserMapper;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import com.orbitworkbench.workspace.application.WorkspaceService;
import com.orbitworkbench.workspace.domain.WorkspaceRecord;
import java.time.Instant;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityService {
    private final AppUserMapper userMapper;
    private final WorkspaceService workspaceService;
    private final PasswordEncoder passwordEncoder;
    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    public IdentityService(AppUserMapper userMapper,
                           WorkspaceService workspaceService,
                           PasswordEncoder passwordEncoder,
                           FindByIndexNameSessionRepository<? extends Session> sessionRepository) {
        this.userMapper = userMapper;
        this.workspaceService = workspaceService;
        this.passwordEncoder = passwordEncoder;
        this.sessionRepository = sessionRepository;
    }

    @Transactional(readOnly = true)
    public boolean initialized() {
        return userMapper.countAll() > 0;
    }

    @Transactional
    public SetupResponse setup(SetupRequest request) {
        if (userMapper.findFirstForUpdate() != null) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT, "系统已经完成初始化");
        }
        String username = request.username().trim();
        if (username.length() < 3 || username.length() > 32) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "username 长度必须为 3-32");
        }
        if (request.password().length() < 6) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "密码长度不能少于 6 位");
        }
        Instant now = Instant.now();
        AppUserRecord user = new AppUserRecord();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setEnabled(true);
        user.setPasswordChangedAt(now);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        try {
            if (userMapper.insert(user) != 1) {
                throw new IllegalStateException("创建用户失败");
            }
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "系统已经完成初始化");
        }
        WorkspaceRecord workspace = workspaceService.createDefault();
        return new SetupResponse(user.getId(), user.getUsername(), workspace.getId());
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(Long id) {
        AppUserRecord user = userMapper.findById(id);
        if (user == null || !user.isEnabled()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED, "用户不存在或已禁用");
        }
        return UserResponse.from(user);
    }

    @Transactional
    public void changePassword(Long id, PasswordChangeRequest request) {
        AppUserRecord user = userMapper.findById(id);
        if (user == null || !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "当前密码不正确");
        }
        if (request.newPassword().length() < 6) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED,
                    "新密码长度不能少于 6 位");
        }
        String newPasswordHash = passwordEncoder.encode(request.newPassword());
        if (userMapper.updatePassword(id, newPasswordHash, user.getPasswordHash()) != 1) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.STATE_CONFLICT,
                    "密码已被其他请求修改，请重新登录后再试");
        }
        Map<String, ? extends Session> sessions = sessionRepository.findByPrincipalName(user.getUsername());
        sessions.keySet().forEach(sessionRepository::deleteById);
    }

    @Transactional
    public void recordSuccessfulLogin(Long id) {
        if (userMapper.updateLastLogin(id) != 1) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED,
                    "当前用户不可用");
        }
    }

    public AppUserRecord findByUsername(String username) {
        return userMapper.findByUsername(username);
    }
}
