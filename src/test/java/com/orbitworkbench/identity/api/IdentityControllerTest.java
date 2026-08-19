package com.orbitworkbench.identity.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orbitworkbench.agent.application.SseHub;
import com.orbitworkbench.identity.api.IdentityDtos.PasswordChangeRequest;
import com.orbitworkbench.identity.api.IdentityDtos.UserResponse;
import com.orbitworkbench.identity.application.IdentityService;
import com.orbitworkbench.identity.application.OrbitUserDetails;
import com.orbitworkbench.identity.domain.AppUserRecord;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;

class IdentityControllerTest {

    private IdentityService identityService;
    private SseHub sseHub;
    private IdentityController controller;

    @BeforeEach
    void setUp() {
        identityService = mock(IdentityService.class);
        sseHub = mock(SseHub.class);
        controller = new IdentityController(
                identityService,
                mock(AuthenticationManager.class),
                mock(SecurityContextRepository.class),
                mock(CsrfTokenRepository.class),
                sseHub);
    }

    @Test
    void logoutClosesExistingSseSubscriptions() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        Authentication authentication = mock(Authentication.class);

        controller.logout(request, response, authentication);

        verify(sseHub).closeAll();
    }

    @Test
    void passwordChangeClosesSseAfterPasswordUpdate() {
        AppUserRecord user = new AppUserRecord();
        user.setId(7L);
        user.setUsername("orbit");
        user.setEnabled(true);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(new OrbitUserDetails(user));
        when(identityService.getUser(7L)).thenReturn(new UserResponse(7L, "orbit"));
        PasswordChangeRequest request = new PasswordChangeRequest(
                "old-password",
                "new-password");

        UserResponse response = controller.changePassword(
                request,
                authentication,
                mock(HttpServletRequest.class));

        assertEquals(7L, response.id());
        InOrder order = inOrder(identityService, sseHub);
        order.verify(identityService).changePassword(7L, request);
        order.verify(sseHub).closeAll();
    }
}
