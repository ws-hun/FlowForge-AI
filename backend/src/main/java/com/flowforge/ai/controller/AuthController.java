package com.flowforge.ai.controller;

import com.flowforge.ai.dto.AuthCredentialsRequest;
import com.flowforge.ai.dto.AuthSessionResult;
import com.flowforge.ai.dto.AuthSetupRequest;
import com.flowforge.ai.dto.AuthStatusResponse;
import com.flowforge.ai.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Arrays;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final boolean secureCookie;

    public AuthController(
            AuthService authService,
            @Value("${flowforge.auth.secure-cookie:false}") boolean secureCookie
    ) {
        this.authService = authService;
        this.secureCookie = secureCookie;
    }

    @GetMapping("/status")
    public AuthStatusResponse status(HttpServletRequest request) {
        return authService.getStatus(readSessionToken(request));
    }

    @PostMapping("/setup")
    public ResponseEntity<AuthStatusResponse> setup(@Valid @RequestBody AuthSetupRequest request) {
        return authenticatedResponse(authService.setup(request), HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthStatusResponse> login(@Valid @RequestBody AuthCredentialsRequest request) {
        return authenticatedResponse(authService.login(request), HttpStatus.OK);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        authService.logout(readSessionToken(request));
        ResponseCookie cookie = buildSessionCookie("", Duration.ZERO);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }

    private ResponseEntity<AuthStatusResponse> authenticatedResponse(
            AuthSessionResult result,
            HttpStatus status
    ) {
        ResponseCookie cookie = buildSessionCookie(result.token(), authService.getSessionDuration());
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(result.status());
    }

    private ResponseCookie buildSessionCookie(String token, Duration maxAge) {
        return ResponseCookie.from(AuthService.SESSION_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
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
}
