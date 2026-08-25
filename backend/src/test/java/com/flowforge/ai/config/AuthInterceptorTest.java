package com.flowforge.ai.config;

import com.flowforge.ai.dto.AuthUserResponse;
import com.flowforge.ai.exception.AuthenticationRequiredException;
import com.flowforge.ai.exception.RequestOriginDeniedException;
import com.flowforge.ai.service.AuthService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthInterceptorTest {

    private final AuthService authService = mock(AuthService.class);
    private final AuthInterceptor interceptor = new AuthInterceptor(authService, "http://localhost:10086");

    @Test
    void allowsPublicHealthAndStatusRequests() {
        assertThat(preHandle("GET", "/api/health", null, null)).isTrue();
        assertThat(preHandle("GET", "/api/auth/status", null, null)).isTrue();
    }

    @Test
    void rejectsCrossSiteWritesEvenForPublicAuthenticationEndpoints() {
        MockHttpServletRequest request = request("POST", "/api/auth/login");
        request.addHeader("Sec-Fetch-Site", "cross-site");

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(RequestOriginDeniedException.class);
    }

    @Test
    void rejectsProtectedApisWithoutAValidSession() {
        when(authService.findAuthenticatedUser(null)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> preHandle("GET", "/api/tasks", null, null))
                .isInstanceOf(AuthenticationRequiredException.class);
    }

    @Test
    void acceptsAValidCookieAndExposesTheAuthenticatedOwner() {
        AuthUserResponse user = new AuthUserResponse(UUID.randomUUID(), "owner@example.com", "Owner");
        when(authService.findAuthenticatedUser("raw-token")).thenReturn(Optional.of(user));
        MockHttpServletRequest request = request("POST", "/api/tasks/run");
        request.setCookies(new Cookie(AuthService.SESSION_COOKIE_NAME, "raw-token"));
        request.addHeader("Origin", "http://127.0.0.1:10086");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        assertThat(request.getAttribute(AuthService.AUTHENTICATED_USER_ATTRIBUTE)).isEqualTo(user);
        verify(authService).findAuthenticatedUser("raw-token");
    }

    private boolean preHandle(String method, String path, String token, String origin) {
        MockHttpServletRequest request = request(method, path);
        if (token != null) {
            request.setCookies(new Cookie(AuthService.SESSION_COOKIE_NAME, token));
        }
        if (origin != null) {
            request.addHeader("Origin", origin);
        }
        return interceptor.preHandle(request, new MockHttpServletResponse(), new Object());
    }

    private MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRequestURI(path);
        return request;
    }
}
