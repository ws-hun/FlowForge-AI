package com.flowforge.ai.config;

import com.flowforge.ai.dto.AuthUserResponse;
import com.flowforge.ai.exception.AuthenticationRequiredException;
import com.flowforge.ai.exception.RequestOriginDeniedException;
import com.flowforge.ai.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;

import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

public class AuthInterceptor implements org.springframework.web.servlet.HandlerInterceptor {

    private final AuthService authService;
    private final Set<String> allowedOrigins;

    public AuthInterceptor(AuthService authService, String frontendUrl) {
        this.authService = authService;
        this.allowedOrigins = resolveAllowedOrigins(frontendUrl);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        if (isPublicPath(request.getRequestURI())) {
            verifyRequestOrigin(request);
            return true;
        }

        verifyRequestOrigin(request);
        AuthUserResponse user = authService.findAuthenticatedUser(readSessionToken(request))
                .orElseThrow(() -> new AuthenticationRequiredException("登录已失效，请重新登录"));
        request.setAttribute(AuthService.AUTHENTICATED_USER_ATTRIBUTE, user);
        return true;
    }

    private boolean isPublicPath(String requestUri) {
        return "/api/health".equals(requestUri)
                || "/api/auth/status".equals(requestUri)
                || "/api/auth/setup".equals(requestUri)
                || "/api/auth/login".equals(requestUri)
                || "/api/auth/logout".equals(requestUri);
    }

    private void verifyRequestOrigin(HttpServletRequest request) {
        if (HttpMethod.GET.matches(request.getMethod()) || HttpMethod.HEAD.matches(request.getMethod())) {
            return;
        }
        if ("cross-site".equalsIgnoreCase(request.getHeader("Sec-Fetch-Site"))) {
            throw new RequestOriginDeniedException("请求来源未被允许");
        }
        String origin = request.getHeader("Origin");
        if (origin != null && !origin.isBlank() && !allowedOrigins.contains(trimTrailingSlash(origin))) {
            throw new RequestOriginDeniedException("请求来源未被允许");
        }
    }

    private String readSessionToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(cookie -> AuthService.SESSION_COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private Set<String> resolveAllowedOrigins(String frontendUrl) {
        String normalized = trimTrailingSlash(frontendUrl);
        Set<String> origins = new LinkedHashSet<>();
        origins.add(normalized);
        try {
            URI uri = URI.create(normalized);
            if ("localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost())) {
                int port = uri.getPort();
                String portSuffix = port < 0 ? "" : ":" + port;
                origins.add(uri.getScheme() + "://localhost" + portSuffix);
                origins.add(uri.getScheme() + "://127.0.0.1" + portSuffix);
            }
        } catch (IllegalArgumentException ignored) {
            // The configured origin remains the only accepted value and startup can continue.
        }
        return Set.copyOf(origins);
    }

    private String trimTrailingSlash(String value) {
        String trimmed = Optional.ofNullable(value).orElse("").trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
