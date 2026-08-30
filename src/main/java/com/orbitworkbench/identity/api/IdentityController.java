package com.orbitworkbench.identity.api;

import com.orbitworkbench.identity.api.IdentityDtos.CsrfResponse;
import com.orbitworkbench.identity.api.IdentityDtos.LoginRequest;
import com.orbitworkbench.identity.api.IdentityDtos.PasswordChangeRequest;
import com.orbitworkbench.identity.api.IdentityDtos.SetupRequest;
import com.orbitworkbench.identity.api.IdentityDtos.SetupResponse;
import com.orbitworkbench.identity.api.IdentityDtos.SetupStatusResponse;
import com.orbitworkbench.identity.api.IdentityDtos.UserResponse;
import com.orbitworkbench.identity.application.IdentityService;
import com.orbitworkbench.identity.application.OrbitUserDetails;
import com.orbitworkbench.shared.api.ApiException;
import com.orbitworkbench.shared.api.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.net.InetAddress;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class IdentityController {
    private final IdentityService identityService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final CsrfTokenRepository csrfTokenRepository;

    public IdentityController(IdentityService identityService,
                              AuthenticationManager authenticationManager,
                              SecurityContextRepository securityContextRepository,
                              CsrfTokenRepository csrfTokenRepository) {
        this.identityService = identityService;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
    }

    @GetMapping("/setup/status")
    public SetupStatusResponse setupStatus() {
        return new SetupStatusResponse(identityService.initialized());
    }

    @PostMapping("/setup")
    public SetupResponse setup(@Valid @RequestBody SetupRequest request, HttpServletRequest httpRequest) {
        if (!isLoopback(httpRequest)) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED,
                    "首次初始化仅允许本机调用");
        }
        return identityService.setup(request);
    }

    @GetMapping("/auth/csrf")
    public CsrfResponse csrf(HttpServletRequest request, HttpServletResponse response) {
        CsrfToken token = csrfTokenRepository.loadToken(request);
        if (token == null) {
            token = csrfTokenRepository.generateToken(request);
            csrfTokenRepository.saveToken(token, request, response);
        }
        return new CsrfResponse(token.getHeaderName());
    }

    @PostMapping("/auth/login")
    public UserResponse login(@Valid @RequestBody LoginRequest request,
                              HttpServletRequest httpRequest,
                              HttpServletResponse httpResponse) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            request.username().trim(), request.password()));
        } catch (AuthenticationException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.AUTHENTICATION_FAILED,
                    "用户名或密码错误");
        }
        if (httpRequest.getSession(false) != null) {
            httpRequest.changeSessionId();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);
        OrbitUserDetails principal = (OrbitUserDetails) authentication.getPrincipal();
        identityService.recordSuccessfulLogin(principal.userId());
        return identityService.getUser(principal.userId());
    }

    @GetMapping("/auth/me")
    public UserResponse me(Authentication authentication) {
        return identityService.getUser(((OrbitUserDetails) authentication.getPrincipal()).userId());
    }

    @PostMapping("/auth/logout")
    public void logout(HttpServletRequest request, HttpServletResponse response,
                       Authentication authentication) {
        new SecurityContextLogoutHandler().logout(request, response, authentication);
    }

    @PutMapping("/auth/password")
    public UserResponse changePassword(@Valid @RequestBody PasswordChangeRequest passwordRequest,
                                       Authentication authentication,
                                       HttpServletRequest request) {
        Long userId = ((OrbitUserDetails) authentication.getPrincipal()).userId();
        identityService.changePassword(userId, passwordRequest);
        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }
        return identityService.getUser(userId);
    }

    private boolean isLoopback(HttpServletRequest request) {
        try {
            return InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress();
        } catch (Exception ignored) {
            return false;
        }
    }
}
